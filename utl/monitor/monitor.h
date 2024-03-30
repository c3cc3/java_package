#ifndef _MONITOR_H
#define _MONITOR_H


#ifdef __cplusplus
extern "C"
{
#endif

typedef struct _mon_val_cell mon_val_cell_t;
struct _mon_val_cell {
	short	x_pos;
	short	y_pos;
	short	x_ind;
	short	y_ind;
	short	len;
	char	*value;
	bool_t	left_flag; /* default is rigth */
};

#ifdef __cplusplus
}
#endif

#endif

