/*
** fq_btree_obj.h
*/
#ifndef _FQ_BTREE_OBJ_H
#define _FQ_BTREE_OBJ_H

#include <stdio.h>
#include <stdlib.h>


#ifdef __cplusplus
extern "C"
{
#endif

struct btree_node {
    char *value;            // all void* types replaced by char*
    struct btree_node *p_left;
    struct btree_node *p_right;
};

//use typedef to make calling the compare function easier
typedef int (*Compare)(const char *, const char *);

typedef struct _fq_btree_obj fq_btree_obj_t;
struct _fq_btree_obj {
    fq_logger_t *l;
    int length;
    struct btree_node *p_root;
    bool (*on_insert)(fq_logger_t *, char *, struct btree_node **, Compare);
    void (*on_inorder)(fq_logger_t *, struct btree_node *);
    void (*on_search)(fq_logger_t *, char *, struct btree_node *, Compare);
    void (*on_delete)(fq_logger_t *, struct btree_node **);
};

bool OpenBTree(fq_logger_t *l, fq_btree_obj_t **obj);
bool CloseBTree(fq_logger_t *l, fq_btree_obj_t *obj);

#ifdef __cplusplus
}
#endif

#endif
