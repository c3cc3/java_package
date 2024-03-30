/* vi: set sw=4 ts=4: */
/*
** fq_hashobj.h
*/

#ifndef _FQ_HASHOBJ_H
#define _FQ_HASHOBJ_H

#include "fq_logger.h"
#include "fq_flock.h"
#include "fq_codemap.h"

#define MAX_CHAIN_LENGTH (8)
#define MAP_MISSING (-3)  /* No such element */
#define MAP_FULL (-2)     /* Hashmap is full */
#define MAP_OMEM (-1)     /* Out of Memory */
#define MAP_OK (0)    /* OK */

#ifdef __cplusplus
extern "C"
{
#endif

#define HASHMAP_INFO_EXTENSION "Hash.info"

#define HASHMAP_HEADER_EXTENSION_NAME "header"
#define HASHMAP_DATA_EXTENSION_NAME "data"

#define MAX_HASHMAP_PATH_LENGTH (128)
#define MAX_HASHMAP_NAME_LENGTH (32)
#define MAX_HASHMAP_DESC_LEN 	(128)
#define MAX_HASHMAP_FULLNAE_LEN	(MAX_HASHMAP_PATH_LENGTH+MAX_HASHMAP_NAME_LENGTH)

typedef struct _hashmap_header_t hashmap_header_t;
struct _hashmap_header_t {
	char	path[MAX_HASHMAP_PATH_LENGTH+1];
	char    hashname[MAX_HASHMAP_NAME_LENGTH+1];
	char    desc[MAX_HASHMAP_DESC_LEN+1];
	char	pathfile[MAX_HASHMAP_FULLNAE_LEN+1];
	char	data_pathfile[MAX_HASHMAP_FULLNAE_LEN+1];
	int		table_length;	/* count of total table length */
	int		curr_elements; /* count of current elements */
	int 	max_key_length;
	int 	max_data_length;
	int		data_file_size;
};

typedef struct _hash_head_obj_t  hash_head_obj_t;
struct _hash_head_obj_t {
    fq_logger_t *l;
    char    *fullname;
    int     fd;
    struct stat st;
    hashmap_header_t   *h; // mmapping pointer.
	int (*on_print)(fq_logger_t *, hash_head_obj_t *);
};

typedef struct _hash_data_obj_t hash_data_obj_t;
struct _hash_data_obj_t {
	fq_logger_t *l;
	char    *fullname;
	int     fd;
	void 	*d;
    struct stat st;
};

typedef struct _hashmap_obj_t hashmap_obj_t;
struct _hashmap_obj_t {
	char *path;
	char *name;
	fq_logger_t *l;

    flock_obj_t *flock;

	hash_head_obj_t	*h;
	hash_data_obj_t	*d;

	int (*on_put)(fq_logger_t *, hashmap_obj_t *, char *key, void *value, int value_size);
	int (*on_insert)(fq_logger_t *, hashmap_obj_t *, char *key, void *value, int value_size);
	int (*on_get)(fq_logger_t *, hashmap_obj_t *, char *key, void **value);
	int (*on_get_and_del)(fq_logger_t *, hashmap_obj_t *, char *key, void *value);
	int (*on_remove)(fq_logger_t *, hashmap_obj_t *, char *key);
	int (*on_clean_table)(fq_logger_t *, hashmap_obj_t *);
	int (*on_get_by_index)(fq_logger_t *, hashmap_obj_t *, int index, char *key, void *value);
};

int CreateHashMapFiles( fq_logger_t *l, char *path, char *name);
int UnlinkHashMapFiles( fq_logger_t *l, char *path, char *name);
int CleanHashMap( fq_logger_t *l, char *path, char *hashname);
int ExtendHashMap( fq_logger_t *l, char *path, char *hashname);
int OpenHashMapFiles(fq_logger_t *l, char *path, char *name, hashmap_obj_t **obj);
int CloseHashMapFiles(fq_logger_t *l, hashmap_obj_t **obj);
int ViewHashMap( fq_logger_t *l, char *path, char *hashname);
int ScanHashMap( fq_logger_t *l, char *path, char *hashname);

int PutHash( fq_logger_t *l, hashmap_obj_t *hash_obj, char *key, char *value);
int InsertHash( fq_logger_t *l, hashmap_obj_t *hash_obj, char *key, char *value);

int GetHash( fq_logger_t *l, hashmap_obj_t *hash_obj, char *key, char **value);
int Sure_GetHash( fq_logger_t *l, hashmap_obj_t *hash_obj, char *key, char **value);

int GetDelHash( fq_logger_t *l, hashmap_obj_t *hash_obj, char *key, char **value);
int DeleteHash( fq_logger_t *l, hashmap_obj_t *hash_obj, char *key);

int LoadingHashMap(fq_logger_t *l, hashmap_obj_t *hash_obj,  char *filename);
int Export_Hash( fq_logger_t *l, hashmap_obj_t *hash_obj, char *export_filename);
int Scan_Hash( fq_logger_t *l, hashmap_obj_t *hash_obj);
int Import_Hash( fq_logger_t *l, char *pathfile, hashmap_obj_t *hash_obj);

#ifdef __cplusplus
}
#endif

#endif
