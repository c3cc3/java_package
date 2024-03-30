#ifndef _SCREEN_H
#define _SCREEN_H

typedef struct _screen {
	char	*old_locale;
	long	_attr_flag;
}screen_t;

#ifdef __cplusplus
extern "C" {
#endif

void init_screen(screen_t *s);
int end_screen(screen_t *s, int exitflag);
int get_col_winsize();
int get_row_winsize();
void draw_bar(int y, int flag);
void center_disp(int y, char *fmt, ...);
void right_disp(int y, char *fmt, ...);
void xy_get_string( int y, int x, char *dst);
void color_mvprint_int( int y, int x, int fg, int bg, int value);
void color_mvprint_str( int y, int x, int fg, int bg, char *value);
void color_mvprint_char( int y, int x, int fg, int bg, char value);
void color_mvprint_float_percent( int y, int x, int fg, int bg, float value);
void c_mvprintw( int y, int x, int fg, int bg, char *fmt, ...);
void c_mvprintw_pair( int y, int x, short pair_no,  int fg, int bg, char *fmt, ...);

#ifdef __cplusplus
}
#endif

#endif
