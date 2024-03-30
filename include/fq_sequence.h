#ifndef _FQ_SEQUENCE_H
#define _FQ_SEQUENCE_H

#ifdef __cplusplus
extern "C" {
#endif

typedef struct _sequence_obj sequence_obj_t;
struct _sequence_obj {
	fq_logger_t	*l;
	char	*path;
	char	*fname;
	char	pathfile[256];
	char	lock_pathfile[256];
	char	*prefix;
	int		total_length;
	int		fd;
	int		lockfd;
	int     (*on_get_sequence)(sequence_obj_t *, char *, size_t );
    int     (*on_reset_sequence)(sequence_obj_t *);
};

int open_sequence_obj( fq_logger_t *l, char *path, char *fname, char *prefix, int total_length, sequence_obj_t **obj);
int close_sequence_obj( sequence_obj_t **obj);

#ifdef __cplusplus
}
#endif

#endif
