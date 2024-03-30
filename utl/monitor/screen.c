/*
** Program name: screen.c
**
** author: Gwisang.Choi
** e-mail: gwisang.choi@oracle.com
** mobile: 010-7202-1516
*/
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>

#include <curses.h>
#include <locale.h>

#include <sys/ioctl.h>
// #include <stropts.h>
// #include <stdarg.h>
//
// This is a solaris only.
// #include <sys/varargs.h>

#include "screen.h"

#define WIN_TRUE	1
#define WIN_FALSE	0

#if 0
#include <curses.h>
main()
{
    int has,can,Colors,Color_pairs,i;

    /* code */

    initscr();
    start_color();

    has=has_colors(); /* TRUE=term has colors */
    can=can_change_color(); /* TRUE=term can change colors */
    Colors=COLORS; /* number of colors term supports */
    Color_pairs=COLOR_PAIRS; /* num color pairs term supports */

    init_pair(1,COLOR_BLUE,COLOR_GREEN); /* init_pair(pairNum,fg,bg) */
    attrset(COLOR_PAIR(1)); /* set color attribute */

    printw("HI THERE1"); /* set some colored text */

    refresh(); /* show text */
    sleep(2); /* time to see it */
    endwin();

    printf("has_colors()=%d COLORS=%d COLOR_PAIRS=%d\n",has,Colors,Color_pairs);
    printf("can_change_color()=%d\n",can);
}
#endif

/*
** for coloring, You have to set the following in .profile.
** export TERM=dtterm
*/
void c_mvprintw( int y, int x, int fg, int bg, char *fmt, ...)
{
    va_list ap;
    char buf[1024];

    va_start(ap, fmt);
    vsprintf(buf, fmt, ap);
    va_end(ap);

    init_pair(1, fg, bg);
    attrset(COLOR_PAIR(1)); /* set color attribute */
    mvprintw( y, x, "%s", buf);
    attrset(COLOR_PAIR(0)); /* set color attribute */

    return;
}
void c_mvprintw_pair( int y, int x, short pair_no,  int fg, int bg, char *fmt, ...)
{
    va_list ap;
    char buf[1024];

    va_start(ap, fmt);
    vsprintf(buf, fmt, ap);
    va_end(ap);

    init_pair(pair_no, fg, bg);
    attrset(COLOR_PAIR(1)); /* set color attribute */
    mvprintw( y, x, "%s", buf);
    attrset(COLOR_PAIR(0)); /* set color attribute */

    return;
}

void color_mvprint_int( int y, int x, int fg, int bg, int value)
{
	init_pair(1, fg, bg);
	attrset(COLOR_PAIR(1)); /* set color attribute */
	mvprintw( y, x, "%d", value);
	attrset(COLOR_PAIR(0)); /* set color attribute */
}
void color_mvprint_str( int y, int x, int fg, int bg, char *value)
{
	init_pair(1, fg, bg);
	attrset(COLOR_PAIR(1)); /* set color attribute */
	mvprintw( y, x, "%s", value);
	attrset(COLOR_PAIR(0)); /* set color attribute */
}
void color_mvprint_char( int y, int x, int fg, int bg, char value)
{
	init_pair(1, fg, bg);
	attrset(COLOR_PAIR(1)); /* set color attribute */
	mvprintw( y, x, "%c", value);
	attrset(COLOR_PAIR(0)); /* set color attribute */
}
void color_mvprint_float_percent( int y, int x, int fg, int bg, float value)
{
	init_pair(1, fg, bg);
	attrset(COLOR_PAIR(1)); /* set color attribute */
	mvprintw( y, x, "%f(%%)", value);
	attrset(COLOR_PAIR(0)); /* set color attribute */
}

void init_screen(screen_t *s)
{
	s->old_locale = setlocale(LC_ALL, "korea");
	initscr();
	start_color();
	noecho();
	nonl();
	cbreak();
	raw();

	intrflush(stdscr, WIN_FALSE);
	keypad(stdscr, WIN_TRUE);

	return;
}
int end_screen(screen_t *s, int exitflag)
{
	clear();
	refresh();
	endwin();

	if(exitflag) exit(0);

	setlocale(LC_ALL, s->old_locale);

	return(0);
}

int get_col_winsize()
{
#ifdef TIOCGWINSZ
	struct winsize size;

	if (ioctl( STDIN_FILENO, TIOCGWINSZ, (char *) &size) >= 0)
		return(size.ws_col);
	else
		return(-1);
#else
	return(80);
#endif

}

int get_row_winsize()
{
#ifdef TIOCGWINSZ
	struct winsize size;

	if (ioctl( STDIN_FILENO, TIOCGWINSZ, (char *) &size) >= 0)
		return(size.ws_row);
	else
        return(-1);
#else
	return(25);
#endif
}

void draw_bar(int y, int flag)
{
	int cols;
	char buf[1024];

	cols = get_col_winsize();

	if(cols < 0 || cols > 256) cols = 80;
	 

	memset(buf, 0x00, sizeof(buf));

	if(flag == 1) 
		memset(buf, '-', cols-1);
	else if(flag == 2)
		memset(buf, '=', cols-1);
	else
		memset(buf, '=', cols-1);

	mvprintw(y, 0, "%s", buf);

	return;
}

void center_disp(int y, char *fmt, ...)
{
	va_list arg_ptr;
	char buf[512];
	int	cols, x, len;

	cols = get_col_winsize();

    va_start(arg_ptr, fmt);
    vsprintf(buf, fmt, arg_ptr);
    va_end(arg_ptr);

	len = strlen(buf);

	x = ((cols-len)/2);

    move(y, 1);
    mvprintw(y, x, "%s", buf);

	return;
}

void right_disp(int y, char *fmt, ...)
{
	va_list arg_ptr;
        char  buf[512];
	int	cols, x, len;

	cols = get_col_winsize();

	va_start(arg_ptr, fmt);
	vsprintf(buf, fmt, arg_ptr);
	va_end(arg_ptr);

	len = strlen(buf);

	x = cols-len;

	move(y, 1);
	mvprintw(y, x,"%s",  buf);

	return;
}

void xy_get_string( int y, int x, char *dst)
{
	int i, c, old_y, old_x;

	echo();
	move(y, x);
	getyx(stdscr, old_y, old_x);

reget:
	for(i=0; i<80; i++) {
		c = getch();
		switch(c) {
			case '\n':
			case '\r': /* enter */
				dst[i]=0x00;
				return;
			case 0x08: /* backspace */
				move(old_y, old_x);
				clrtoeol();
				refresh();
				dst[0]=0x00;
				x = old_x;
				i=0;
				goto reget;
			default:
				move(y, ++x);
				dst[i] = c;
				break;
		}
	}
	noecho();
	return;
}
