/*
** fq_btree.h
*/
#ifndef _FQ_BTREE_H
#define _FQ_BTREE_H

#include <stdio.h>
#include <stdlib.h>


#ifdef __cplusplus
extern "C"
{
#endif

struct node
{
    int data; //node will store an integer
    struct node *right_child; // right child
    struct node *left_child; // left child
};

struct node* search(struct node *root, int x);
struct node* find_minimum(struct node *root);
struct node* new_node(int x);
struct node* insert(struct node *root, int x);
struct node* delete(struct node *root, int x);
void inorder(struct node *root);

#ifdef __cplusplus
}
#endif

#endif
