#define _GNU_SOURCE

#include <jni.h>
#include "FQueue.h"
#include <pthread.h>
#include <stdint.h>
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "fqueue.h"
#include "fq_logger.h"

#define JNI_MAX_QUEUES       32
#define JNI_RECV_BUFFER_SIZE 65536
#define JNI_UNLINK_NAME_SIZE 512

typedef struct {
    fqueue_obj_t *obj;
    int xa_pending;
    long xa_seq;
} jni_queue_slot_t;

static fq_logger_t *g_logger = NULL;
static jni_queue_slot_t g_queues[JNI_MAX_QUEUES];
static pthread_mutex_t g_mutex = PTHREAD_MUTEX_INITIALIZER;

/*
 * This implementation intentionally serializes JNI calls with one mutex.
 * FileQueue's XA dequeue keeps its dequeue lock until commit/cancel, so only
 * one outstanding XA message per queue is allowed by this wrapper.
 */
static int valid_queue_id(jint id)
{
    return id >= 0 && id < JNI_MAX_QUEUES && g_queues[id].obj != NULL;
}

static jclass find_class(JNIEnv *env, const char *name)
{
    jclass cls = (*env)->FindClass(env, name);
    return cls;
}

JNIEXPORT jint JNICALL
Java_FQueue_openLogger(JNIEnv *env, jobject self, jstring log_file, jint log_level)
{
    const char *path;
    int rc;

    (void)self;

    if (log_file == NULL || log_level < FQ_LOG_TRACE_LEVEL ||
        log_level > FQ_LOG_EMERG_LEVEL) {
        return -1;
    }

    path = (*env)->GetStringUTFChars(env, log_file, NULL);
    if (path == NULL) {
        return -1; /* JVM has raised an exception */
    }

    pthread_mutex_lock(&g_mutex);

    if (g_logger != NULL) {
        rc = -1;
    } else {
        rc = fq_open_file_logger(&g_logger, path, (int)log_level);
        if (rc <= 0) {
            g_logger = NULL;
        }
    }

    pthread_mutex_unlock(&g_mutex);
    (*env)->ReleaseStringUTFChars(env, log_file, path);
    return (jint)rc;
}

JNIEXPORT jint JNICALL
Java_FQueue_closeLogger(JNIEnv *env, jobject self)
{
    int i, rc;

    (void)env;
    (void)self;

    pthread_mutex_lock(&g_mutex);

    if (g_logger == NULL) {
        pthread_mutex_unlock(&g_mutex);
        return -1;
    }

    for (i = 0; i < JNI_MAX_QUEUES; ++i) {
        if (g_queues[i].obj != NULL) {
            pthread_mutex_unlock(&g_mutex);
            return -1; /* All queues must be closed first. */
        }
    }

    rc = fq_close_file_logger(&g_logger);
    if (rc <= 0) {
        /* Keep the pointer as the logger implementation may still be alive. */
    }

    pthread_mutex_unlock(&g_mutex);
    return (jint)rc;
}

JNIEXPORT jint JNICALL
Java_FQueue_openQueue(JNIEnv *env, jobject self, jstring jpath, jstring jqname)
{
    const char *path = NULL;
    const char *qname = NULL;
    fqueue_obj_t *obj = NULL;
    int i, rc = -1;

    (void)self;

    if (jpath == NULL || jqname == NULL) {
        return -1;
    }

    path = (*env)->GetStringUTFChars(env, jpath, NULL);
    if (path == NULL) {
        return -1;
    }

    qname = (*env)->GetStringUTFChars(env, jqname, NULL);
    if (qname == NULL) {
        (*env)->ReleaseStringUTFChars(env, jpath, path);
        return -1;
    }

    pthread_mutex_lock(&g_mutex);

    if (g_logger == NULL) {
        goto done;
    }

    for (i = 0; i < JNI_MAX_QUEUES; ++i) {
        if (g_queues[i].obj == NULL) {
            break;
        }
    }
    if (i == JNI_MAX_QUEUES) {
        goto done;
    }

    /*
     * This wrapper does not use monitor/heartbeat arguments.
     * OpenFileQueue returns TRUE on success and FALSE on failure.
     */
    if (OpenFileQueue(g_logger, NULL, NULL, NULL, NULL,
                      (char *)path, (char *)qname, &obj) == TRUE && obj != NULL) {
        g_queues[i].obj = obj;
        g_queues[i].xa_pending = 0;
        g_queues[i].xa_seq = 0;
        rc = i;
    } else {
        if (obj != NULL) {
            /* Defensive cleanup if the library leaves a partial object. */
            CloseFileQueue(g_logger, &obj);
        }
        rc = -1;
    }

done:
    pthread_mutex_unlock(&g_mutex);
    (*env)->ReleaseStringUTFChars(env, jqname, qname);
    (*env)->ReleaseStringUTFChars(env, jpath, path);
    return (jint)rc;
}

JNIEXPORT jint JNICALL
Java_FQueue_closeQueue(JNIEnv *env, jobject self, jint queue_id)
{
    int rc;

    (void)env;
    (void)self;

    pthread_mutex_lock(&g_mutex);

    if (!valid_queue_id(queue_id) || g_queues[queue_id].xa_pending) {
        pthread_mutex_unlock(&g_mutex);
        return -1;
    }

    rc = CloseFileQueue(g_logger, &g_queues[queue_id].obj);
    if (rc == TRUE) {
        g_queues[queue_id].obj = NULL;
        g_queues[queue_id].xa_pending = 0;
        g_queues[queue_id].xa_seq = 0;
    }

    pthread_mutex_unlock(&g_mutex);
    return (jint)rc;
}

JNIEXPORT jobject JNICALL
Java_FQueue_enQueue(JNIEnv *env, jobject self, jint queue_id,
                    jbyteArray jdata, jint mode)
{
    jsize len;
    jbyte *buf = NULL;
    long seq = 0L;
    long run_time = 0L;
    int rc;
    jobject result = NULL;
    jclass result_cls = NULL;
    jmethodID ctor;

    (void)self;

    if (jdata == NULL ||
        mode < FQueue_EN_NORMAL_MODE ||
        mode > FQueue_EN_FULL_BACKUP_MODE) {
        return NULL;
    }

    len = (*env)->GetArrayLength(env, jdata);
    if (len < 0 || len == INT_MAX) {
        return NULL;
    }

    /*
     * Existing FileQueue callers pass buffer_len = data_len + 1.
     * Copy arbitrary bytes using the explicit data length; do not use strlen.
     */
    buf = (jbyte *)malloc((size_t)len + 1U);
    if (buf == NULL) {
        return NULL;
    }

    if (len > 0) {
        (*env)->GetByteArrayRegion(env, jdata, 0, len, buf);
        if ((*env)->ExceptionCheck(env)) {
            free(buf);
            return NULL;
        }
    }
    buf[len] = 0;

    pthread_mutex_lock(&g_mutex);

    if (!valid_queue_id(queue_id) || g_logger == NULL ||
        g_queues[queue_id].xa_pending) {
        pthread_mutex_unlock(&g_mutex);
        free(buf);
        return NULL;
    }

    rc = g_queues[queue_id].obj->on_en(
        g_logger,
        g_queues[queue_id].obj,
        (enQ_mode_t)mode,
        (const unsigned char *)buf,
        (int)len + 1,
        (size_t)len,
        &seq,
        &run_time);

    pthread_mutex_unlock(&g_mutex);
    free(buf);

    result_cls = find_class(env, "FQueue$EnqueueResult");
    if (result_cls == NULL) {
        return NULL;
    }

    ctor = (*env)->GetMethodID(env, result_cls, "<init>", "(IJJ)V");
    if (ctor == NULL) {
        return NULL;
    }

    result = (*env)->NewObject(env, result_cls, ctor,
                               (jint)rc, (jlong)seq, (jlong)run_time);
    return result;
}

JNIEXPORT jobject JNICALL
Java_FQueue_deQueueXA(JNIEnv *env, jobject self, jint queue_id)
{
    unsigned char *buf = NULL;
    char unlink_filename[JNI_UNLINK_NAME_SIZE];
    long seq = 0L;
    long run_time = 0L;
    int rc;
    int xa_token_created = 0;
    jbyteArray jdata = NULL;
    jstring junlink = NULL;
    jclass msg_cls = NULL;
    jmethodID ctor;
    jobject result = NULL;

    (void)self;

    buf = (unsigned char *)calloc(JNI_RECV_BUFFER_SIZE, 1U);
    if (buf == NULL) {
        return NULL;
    }
    memset(unlink_filename, 0, sizeof(unlink_filename));

    pthread_mutex_lock(&g_mutex);

    if (!valid_queue_id(queue_id) || g_logger == NULL ||
        g_queues[queue_id].xa_pending) {
        pthread_mutex_unlock(&g_mutex);
        free(buf);
        return NULL;
    }

    rc = g_queues[queue_id].obj->on_de_XA(
        g_logger, g_queues[queue_id].obj,
        buf, JNI_RECV_BUFFER_SIZE, &seq, &run_time, unlink_filename);

    if (rc <= 0) {
        /*
         * API has no separate dequeue-status result type. Return null for
         * empty/error/manager-stop results; details remain in FileQueue logs.
         */
        pthread_mutex_unlock(&g_mutex);
        free(buf);
        return NULL;
    }

    if (rc > JNI_RECV_BUFFER_SIZE) {
        /*
         * Defensive check. The FileQueue implementation should not return a
         * length larger than dstlen; large-file XA copies must also respect it.
         */
        pthread_mutex_unlock(&g_mutex);
        free(buf);
        return NULL;
    }

    g_queues[queue_id].xa_pending = 1;
    g_queues[queue_id].xa_seq = seq;
    xa_token_created = 1;
    pthread_mutex_unlock(&g_mutex);

    jdata = (*env)->NewByteArray(env, (jsize)rc);
    if (jdata == NULL) {
        goto dequeue_java_error;
    }

    (*env)->SetByteArrayRegion(env, jdata, 0, (jsize)rc, (const jbyte *)buf);
    free(buf);
    buf = NULL;
    if ((*env)->ExceptionCheck(env)) {
        goto dequeue_java_error;
    }

    junlink = (*env)->NewStringUTF(env, unlink_filename);
    if (junlink == NULL) {
        goto dequeue_java_error;
    }

    msg_cls = find_class(env, "FQueue$FQueueMessage");
    if (msg_cls == NULL) {
        goto dequeue_java_error;
    }

    ctor = (*env)->GetMethodID(env, msg_cls, "<init>", "(I[BJJLjava/lang/String;)V");
    if (ctor == NULL) {
        goto dequeue_java_error;
    }

    result = (*env)->NewObject(env, msg_cls, ctor,
                               queue_id, jdata, (jlong)seq,
                               (jlong)run_time, junlink);
    if (result != NULL) {
        return result;
    }

dequeue_java_error:
    if (buf != NULL) {
        free(buf);
    }
    /*
     * If the JVM could not create the Java message token, cancel the XA
     * dequeue so the queue is not left locked without a token to commit it.
     */
    if (xa_token_created) {
        pthread_mutex_lock(&g_mutex);
        if (valid_queue_id(queue_id) &&
            g_queues[queue_id].xa_pending &&
            g_queues[queue_id].xa_seq == seq) {
            long cancel_runtime = run_time;
            (void)g_queues[queue_id].obj->on_cancel_XA(
                g_logger, g_queues[queue_id].obj, seq, &cancel_runtime);
            g_queues[queue_id].xa_pending = 0;
            g_queues[queue_id].xa_seq = 0;
        }
        pthread_mutex_unlock(&g_mutex);
    }
    return NULL;
}

static int get_message_fields(JNIEnv *env, jobject message,
                              jint *queue_id, jlong *seq,
                              jlong *run_time, jstring *junlink)
{
    jclass cls;
    jfieldID fid_queue, fid_seq, fid_runtime, fid_unlink;

    if (message == NULL) {
        return -1;
    }

    cls = (*env)->GetObjectClass(env, message);
    if (cls == NULL) {
        return -1;
    }

    fid_queue = (*env)->GetFieldID(env, cls, "queueId", "I");
    fid_seq = (*env)->GetFieldID(env, cls, "seq", "J");
    fid_runtime = (*env)->GetFieldID(env, cls, "runTime", "J");
    fid_unlink = (*env)->GetFieldID(env, cls, "unlinkFilename", "Ljava/lang/String;");
    if (fid_queue == NULL || fid_seq == NULL ||
        fid_runtime == NULL || fid_unlink == NULL) {
        return -1;
    }

    *queue_id = (*env)->GetIntField(env, message, fid_queue);
    *seq = (*env)->GetLongField(env, message, fid_seq);
    *run_time = (*env)->GetLongField(env, message, fid_runtime);
    *junlink = (jstring)(*env)->GetObjectField(env, message, fid_unlink);
    return 0;
}

JNIEXPORT jint JNICALL
Java_FQueue_commit(JNIEnv *env, jobject self, jobject message)
{
    jint queue_id;
    jlong seq, run_time;
    jstring junlink = NULL;
    const char *unlink_filename = NULL;
    int rc;
    long c_run_time;

    (void)self;

    if (get_message_fields(env, message, &queue_id, &seq, &run_time, &junlink) != 0) {
        return -1;
    }

    if (junlink != NULL) {
        unlink_filename = (*env)->GetStringUTFChars(env, junlink, NULL);
        if (unlink_filename == NULL) {
            return -1;
        }
    }

    pthread_mutex_lock(&g_mutex);

    if (!valid_queue_id(queue_id) ||
        !g_queues[queue_id].xa_pending ||
        g_queues[queue_id].xa_seq != (long)seq ||
        g_logger == NULL) {
        pthread_mutex_unlock(&g_mutex);
        if (unlink_filename != NULL) {
            (*env)->ReleaseStringUTFChars(env, junlink, unlink_filename);
        }
        return -1;
    }

    c_run_time = (long)run_time;
    rc = g_queues[queue_id].obj->on_commit_XA(
        g_logger, g_queues[queue_id].obj, (long)seq,
        &c_run_time, (char *)(unlink_filename ? unlink_filename : ""));

    /* A failed commit can also release the FileQueue dequeue lock. */
    g_queues[queue_id].xa_pending = 0;
    g_queues[queue_id].xa_seq = 0;

    pthread_mutex_unlock(&g_mutex);

    if (unlink_filename != NULL) {
        (*env)->ReleaseStringUTFChars(env, junlink, unlink_filename);
    }
    return (jint)rc;
}

JNIEXPORT jint JNICALL
Java_FQueue_cancel(JNIEnv *env, jobject self, jobject message)
{
    jint queue_id;
    jlong seq, run_time;
    jstring junlink = NULL;
    int rc;
    long c_run_time;

    (void)self;

    if (get_message_fields(env, message, &queue_id, &seq, &run_time, &junlink) != 0) {
        return -1;
    }

    pthread_mutex_lock(&g_mutex);

    if (!valid_queue_id(queue_id) ||
        !g_queues[queue_id].xa_pending ||
        g_queues[queue_id].xa_seq != (long)seq ||
        g_logger == NULL) {
        pthread_mutex_unlock(&g_mutex);
        return -1;
    }

    c_run_time = (long)run_time;
    rc = g_queues[queue_id].obj->on_cancel_XA(
        g_logger, g_queues[queue_id].obj,
        (long)seq, &c_run_time);

    g_queues[queue_id].xa_pending = 0;
    g_queues[queue_id].xa_seq = 0;

    pthread_mutex_unlock(&g_mutex);
    return (jint)rc;
}
