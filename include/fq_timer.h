/*
** fq_timer.h
*/

#include <time.h>

#define FQ_TIMER_H_VERSION "1,0,0"
#ifndef _FQ_TIMER_H
#define _FQ_TIMER_H

#ifdef __cplusplus
extern "C" {
#endif

/* Compile with gcc -lrt -lm */
#define CLOCK CLOCK_MONOTONIC

typedef struct _StopWatch_t StopWatch_t;
struct _StopWatch_t {
	struct timespec requestStart;
	struct timespec requestEnd;
	struct timespec req;
	double	elapsed;
	int		started;
	int		stopped;

	void (*Start)(StopWatch_t *obj);
	void (*Work)(StopWatch_t *obj, int );
	void (*Stop)(StopWatch_t *obj);
	double (*GetElapsed)(StopWatch_t *obj);
	void (*PrintElapsed)(StopWatch_t *obj);
};

int open_StopWatch( StopWatch_t **obj);
int close_StopWatch( StopWatch_t **obj);

void start_timer();
double end_timer();

#ifdef __cplusplus
}
#endif

#endif

