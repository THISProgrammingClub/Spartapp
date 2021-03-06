package hackthis.team.spartapp;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.MotionEventCompat;
import android.text.Layout;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.DatePicker;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import cn.leancloud.AVException;
import cn.leancloud.AVOSCloud;


import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;

public class Schedule extends RefreshableFragment {

    //context
    private Activity mActivity;

    private GregorianCalendar browsingTime; //the date for the exhibited schedule
    private GregorianCalendar focusTime; //the date that contains the next period (will be enlarged)
                                        //this variable sets to the next school day

    //basic info
    private int[] month_lengths = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
    private String[] month_names = {"January", "February", "March", "April",
                "May", "June", "July", "August", "September", "October",
                "November", "December"};

    //return the length of the input month, or actually the month of the input 'date' var
    //0 is Jan
    private int month_length(GregorianCalendar date){
        int month = date.get(Calendar.MONTH);
        int year = date.get(Calendar.YEAR);
        //Log.d("MONTHL", String.valueOf(year));
        //Log.d("MONTHL", String.valueOf(month));
        if(month == 1){//feburary is special!
            if(!is366(year)){
                //Log.d("MONTHL", String.valueOf(month_lengths[month]));
                return month_lengths[month];
            }
            else{
                //Log.d("MONTHL", String.valueOf(month_lengths[month]+1));
                return month_lengths[month]+1;
            }
        }
        else{
            //Log.d("MONTHL", String.valueOf(month_lengths[month]));
            return month_lengths[month];
        }
    }

    //big year or small year (gregorian method)
    private boolean is366(int year){
        if(year % 4 == 0){
            if(year % 100 == 0){
                return year % 400 == 0;
            }
            return true;
        }
        return false;
    }

    //storage of online data
    private HashMap<String, Subject[]> subjectTable;

    ArrayAdapter<ClassPeriod> adapter;

    //android views
    ImageView listBackground;
    ListView lv;//the list that contains the schedule

    public int unitHeight;

    //refresh button
    View.OnClickListener FETCH = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent login = new Intent(mActivity, LoginActivity.class);
            startActivity(login);
        }
    };

    //left arrow button
    View.OnClickListener LEFT = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            browsingTime.add(Calendar.MONTH, -1);
            Calendar cal = Calendar.getInstance();
            GregorianCalendar gal = new GregorianCalendar();
            gal.setTime(new Date());
            cal.set(browsingTime.get(Calendar.YEAR), browsingTime.get(Calendar.MONTH), 1);
            if(browsingTime.get(Calendar.MONTH) == gal.get(Calendar.MONTH)) {
                browsingTime.set(Calendar.DATE, gal.get(Calendar.DATE));
            }
            else {
                browsingTime.set(Calendar.DATE, cal.getActualMaximum(Calendar.DATE));
            }
            updateTitleBar();
            load();
        }
    };

    //right arrow button
    View.OnClickListener RIGHT = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            browsingTime.add(Calendar.MONTH, 1);
            GregorianCalendar gal = new GregorianCalendar();
            gal.setTime(new Date());
            if(browsingTime.get(Calendar.MONTH) == gal.get(Calendar.MONTH)) {
                browsingTime.set(Calendar.DATE, gal.get(Calendar.DATE));
            }
            else {
                browsingTime.set(Calendar.DATE, 1);
            }
            updateTitleBar();
            load();
        }
    };
    //these are for the popup calendar menu when clicking the down arrow button
    PopupWindow popup;
    CalendarView expcal;
    LinearLayout cal;
    Boolean expanded = false;
    //down arrow button
    View.OnClickListener EXPAND = new View.OnClickListener(){
        @Override
        public void onClick(View v){
            if(expanded) {
                popup.showAsDropDown(mActivity.findViewById(R.id.expand_calendar));
                ((mActivity.findViewById(R.id.expand_calendar)))
                        .setBackground(ContextCompat.getDrawable(mActivity, R.drawable.arrow_down));
                popup.dismiss();
                expanded = false;
            }else {
                popup.showAsDropDown(mActivity.findViewById(R.id.expand_calendar));
                ((mActivity.findViewById(R.id.expand_calendar)))
                        .setBackground(ContextCompat.getDrawable(mActivity, R.drawable.arrow_up));
                expanded = true;
            }
        }
    };
    //for every date button
    View.OnClickListener DATE = new View.OnClickListener() {
        public void onClick(View v) {
            //v.getTag returns the 'tag' of each date button. See details of the tag in the code for the date button -> it contains a number, which is the date
            browsingTime.set(Calendar.DATE, (int)v.getTag());
            //RadioGroup rg = (RadioGroup)getView().findViewById(R.id.date_radio_group);
            autoscroll();
            load();
            //go to the selected date
            Calendar cal = Calendar.getInstance();
            cal.set(browsingTime.get(Calendar.YEAR), browsingTime.get(Calendar.MONTH), browsingTime.get(Calendar.DATE));
            expcal.setDate(cal.getTimeInMillis());
        }
    };

    String school;
    //layout dimensions & parameters. will be used repeatedly
    RadioGroup.LayoutParams date_params;
    LinearLayout.LayoutParams wk_params;

    //starting times of each period
    HashMap<String, int[]> regularPeriodBeginning;
    HashMap<String, int[]> wednesdayPeriodBeginning;

    //the last length of the date radio group before its update, used in updateTitleBar()
    int last_date_length;

    //find schedule for browsingTime and display on screen, called when fragment is initialized and when date picker gets clicked
    public void load(){
        LogUtil.d("spartapp_log",browsingTime.get(Calendar.DATE)+" "+month_names[browsingTime.get(Calendar.MONTH)]);
        LogUtil.d("sche_time",browsingTime.toString());

        //initialization: transform the current 'browsingTime' into separate variables
        int month = browsingTime.get(Calendar.MONTH) + 1;
        int yr =  browsingTime.get(Calendar.YEAR);
        int day = browsingTime.get(Calendar.DATE);
        String m = month<10?"0"+Integer.toString(month):Integer.toString(month);
        String d = day<10?"0"+Integer.toString(day):Integer.toString(day);
        LogUtil.d("TIME", Integer.toString(yr)+"-"+m+"-"+d);
        Subject[] subs = subjectTable.get(Integer.toString(yr)+"-"+m+"-"+d);

        //LogUtil.d("SCHEDULE", subjectTable.get("2018-09-13")[0].name());
        //every day's schedule has 8 slots. not every slot has a period in it
        if(subs != null) { //if the schedule for that day is empty

                if(school.equals("THIS")) {
                    //for THIS
                    for (int i = 1; i < subs.length; i+=2) {
                        //if a B period is the same with the last A period, like if you have BC for 3A and 3B, then the BC in 3B will be removed
                        //so that the schedule doesn't show duplicate classes
                        if (subs[i] != null && subs[i-1]!=null && subs[i].equals(subs[i-1]))
                            subs[i] = null;
                    }
                }
                //just ignore this for now
                else if (school.equals("ISB")){
                    if(browsingTime.get(Calendar.DAY_OF_WEEK) == Calendar.WEDNESDAY)
                        subs[2] = null;
                }
                //go through every non-null class (that is, classes that aren't removed) and put them on the schedule
                for (Subject i : subs) {
                    if (i != null) {
                        List<ClassPeriod> periods = new ArrayList<>(8);
                        for (int j = 0; j < subs.length; j++) {
                            if (subs[j] != null)
                                periods.add(new ClassPeriod(subs[j], j));//add to list
                        }

                        if (browsingTime.get(Calendar.MONTH)==focusTime.get(Calendar.MONTH) && browsingTime.get(Calendar.DATE) == focusTime.get(Calendar.DATE)){
                            GregorianCalendar gal = new GregorianCalendar();
                            gal.setTime(new Date());
                            //focus time is when the next class is. if it's a school day & before school ends, then the 'next period' is at today
                            //if it's after a school time and tomorrow is a school day, then the 'next period' is at tomorrow
                            //...etc
                            if (focusTime.get(Calendar.MONTH) == gal.get(Calendar.MONTH) && focusTime.get(Calendar.DATE) == gal.get(Calendar.DATE)) {
                                int[] beginning = (new GregorianCalendar()).get(Calendar.DAY_OF_WEEK) ==
                                        Calendar.WEDNESDAY ? wednesdayPeriodBeginning.get(school) :
                                        regularPeriodBeginning.get(school);
                                int temp = new GregorianCalendar().get(Calendar.HOUR_OF_DAY)*100+new GregorianCalendar().get(Calendar.MINUTE);
                                for (ClassPeriod c : periods) {
                                    if (beginning[c.period] > temp) {
                                        c.focus = true;//a focused period
                                        break;
                                    }
                                }
                            } else {
                                //set focus to first period of next school day
                                periods.get(0).focus = true;
                            }
                        }

                        //convert periods into listview items
                        adapter = new PeriodAdapter(mActivity, R.layout.period_small, periods);
                        listBackground.setImageDrawable(null);
                        lv.setAdapter(adapter);

                        break;
                    }
                }
            }
        else{//for an empty day
            ArrayList<ClassPeriod> empty = new ArrayList<>(0);
            adapter = new PeriodAdapter(mActivity, R.layout.period_small, empty);
            LogUtil.d("SCHE",Integer.toString(browsingTime.get(Calendar.DAY_OF_WEEK)));
            if(browsingTime.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY
                    || browsingTime.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                listBackground.setImageResource(R.drawable.weekend);//for weekends, use this icon (flag thingy) in the background
            }
            else{
                listBackground.setImageResource(R.drawable.vacation);//else, use this one (the plane thingy)
            }
            lv.setAdapter(adapter);
        }
    }

    //initiate
    public void onAttach(Context context) {
        super.onAttach(context);
        LogUtil.d("SCHE", "onattach");
        mActivity = (Activity) context;
        //browsingTime is set to begin 'today'
        browsingTime = new GregorianCalendar();
        browsingTime.setTime(new Date());

        regularPeriodBeginning = new HashMap<>(2);
        wednesdayPeriodBeginning = new HashMap<>(2);

        //times for beginning of periods
        regularPeriodBeginning.put("THIS", new int[] {815, 855, 950, 1035, 1122, 1305, 1355, 1435});
        wednesdayPeriodBeginning.put("THIS", new int[] {815, 835, 900, 920, 1045, 1130, 1300, 1320});

        regularPeriodBeginning.put("ISB", new int[] {815, 950, 1155, 1225, 1400});
        wednesdayPeriodBeginning.put("ISB",new int[] {815, 945, 1110, 1305});

        //loads the 'shared preferences' stored in local files
        SharedPreferences sp = getActivity().getSharedPreferences("clubs", Context.MODE_PRIVATE);
        school = sp.getString("school", "");

        //dimensions of layouts
        date_params = new RadioGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        date_params.setMargins(25,0,25,0);
        wk_params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wk_params.setMargins(25, 0, 25, 0);

        //try to read schedule from local files
        try {
            subjectTable = getSchedule();
            LogUtil.d("SCHE", "got");
        }
        catch(Exception e){
            LogUtil.d("ERR", "getSchedule failed");
        }
        //figure out where the next period is
        focusTime = getFocusedDate();
        LogUtil.d("focustime",focusTime.toString());

    }
    @Override
    public void onStart() {
        super.onStart();

        refresh();
    }

    public void refresh(){

        //reset time variables
        focusTime = getFocusedDate();
        browsingTime = new GregorianCalendar();
        browsingTime.setTime(new Date());

        //re-read local files
        SharedPreferences sp = getActivity().getSharedPreferences("clubs", Context.MODE_PRIVATE);
        school = sp.getString("school", "");

        LogUtil.d("sche_onstart","onstart called "+browsingTime.toString());

        //reset UI
        updateTitleBar();
        load();
    }

    //generate the view when called. this is only called by the system, not by the programmer/user
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.schedule, container, false);
        //set onclick events
        ImageButton f = (ImageButton) root.findViewById(R.id.schedule_fetch);
        f.setOnClickListener(FETCH);
        final ImageButton l = (ImageButton) root.findViewById(R.id.schedule_left);
        l.setOnClickListener(LEFT);
        final ImageButton r = (ImageButton) root.findViewById(R.id.schedule_right);
        r.setOnClickListener(RIGHT);
        final Button excal = (Button) root.findViewById(R.id.expand_calendar);
        excal.setOnClickListener(EXPAND);

        cal = (LinearLayout)inflater.inflate(R.layout.calendar_popup, null);
        popup = new PopupWindow(cal, ViewGroup.LayoutParams.MATCH_PARENT, ActionBar.LayoutParams.WRAP_CONTENT);
        popup.setContentView(cal);
        //popup.setHeight(500);
        popup.setOutsideTouchable(false);

        setCalendarToMonth();

        TextView m = (TextView) root.findViewById(R.id.schedule_month);
        m.setText(month_text());

        listBackground = (ImageView) root.findViewById(R.id.schedule_list_background);

        //initialize list of date-buttons
        LinearLayout daywk = (LinearLayout) root.findViewById(R.id.days_wk);
        RadioGroup rg = (RadioGroup) root.findViewById(R.id.date_radio_group);
        for(int i = 0; i < month_length(browsingTime); i++){
            RadioButton rb = (RadioButton) mActivity.getLayoutInflater().inflate(R.layout.date_button, null);
            rb.setOnClickListener(DATE);
            rb.setText(Integer.toString(i+1));
            rb.setTag(i+1);
            rb.setLayoutParams(date_params);
            rg.addView(rb, i);

            TextView dwk = (TextView) mActivity.getLayoutInflater().inflate(R.layout.date_wk, null);
            dwk.setText(dayInWeek(browsingTime.get(Calendar.YEAR), browsingTime.get(Calendar.MONTH), i+1).substring(0,1));
            dwk.setLayoutParams(wk_params);
            daywk.addView(dwk);
        }
        Log.d("BT month length", Integer.toString(month_length(browsingTime)));
        Log.d("BT month name", month_names[browsingTime.get(Calendar.MONTH)]);
        Log.d("BT browsingTime.date", Integer.toString(browsingTime.get(Calendar.DATE)));
        ((RadioButton)rg.getChildAt(browsingTime.get(Calendar.DATE)-1)).setChecked(true);
        HorizontalScrollView scroll = (HorizontalScrollView) root.findViewById(R.id.date_scroll);

        scroll.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                                                                   @Override
                                                                   public void onGlobalLayout() {
                                                                       autoscroll();
                                                                   }
                                                               });
        scroll.setSmoothScrollingEnabled(true);
        last_date_length = month_length(browsingTime);


        //fix week day labels
        final HorizontalScrollView wkday = (HorizontalScrollView)root.findViewById(R.id.day_scroll);
        wkday.setOnTouchListener( new View.OnTouchListener(){
            @Override
            public boolean onTouch(View v, MotionEvent event){
                return true;
            }
        });

        final HorizontalScrollView mday = (HorizontalScrollView)root.findViewById(R.id.date_scroll);
        mday.getViewTreeObserver().addOnScrollChangedListener(new ViewTreeObserver.OnScrollChangedListener() {
            @Override
            public void onScrollChanged() {
                int scrollX = mday.getScrollX();
                Log.d("RESCROL", Integer.toString(scrollX));
                wkday.setScrollX(scrollX);
            }
        });

        /*
        CalendarView wkcal = (CalendarView)root.findViewById(R.id.weekdaylabel);
        wkcal.setOnTouchListener(new View.OnTouchListener(){
            @Override
            public boolean onTouch(View v, MotionEvent event){
                return true;
            }
        });


        ScrollView datepicker = (ScrollView)root.findViewById(R.id.datepickerscroll);
        wkday.setOnTouchListener( new View.OnTouchListener(){
            @Override
            public boolean onTouch(View v, MotionEvent event){
                return true;
            }
        });


        CalendarView cal = (CalendarView)root.findViewById(R.id.datepicker);
        cal.setOnDateChangeListener( new CalendarView.OnDateChangeListener(){
            public void onSelectedDayChange(CalendarView calendarView, int year, int month, int dayOfMonth){
                browsingTime.set(Calendar.DATE, dayOfMonth);
                //autoscroll?
                load();
            }
        });
        MyScrollView dpscroll = (MyScrollView)root.findViewById(R.id.datepickerscroll);
*/
        //generate the list view
        lv = (ListView) root.findViewById(R.id.schedule_list);
        lv.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                unitHeight = (int)(lv.getHeight()*0.25);
                lv.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                refresh();
            }
        });
        lv.setOnTouchListener(new OnSwipeTouchListener(mActivity) {
            /*
            public void onSwipeTop(){
                if(expanded) {
                    excal.callOnClick();
                }
            }

            public void onSwipeBottom(){
                if(!expanded) {
                    excal.callOnClick();
                }
            }
            */

            public void onSwipeRight() {
                if(!expanded) {
                    //decrease date
                    int month = browsingTime.get(Calendar.MONTH);
                    browsingTime.add(Calendar.DATE, -1);
                    if(browsingTime.get(Calendar.MONTH) != month)
                        updateTitleBar();
                    else
                        autoscroll();
                    load();
                }
                else{
                    l.callOnClick();
                }
            }

            public void onSwipeLeft() {
                //increase date
                if(!expanded) {
                    int month = browsingTime.get(Calendar.MONTH);
                    browsingTime.add(Calendar.DATE, 1);
                    if(browsingTime.get(Calendar.MONTH) != month)
                        updateTitleBar();
                    else
                        autoscroll();
                    load();
                }
                else{
                    r.callOnClick();
                }
            }
        });

        return root;
    }

    //scroll the upper list of buttons to the correct position--that is, the button for the current browsing date should be centered
    private void autoscroll(){
        HorizontalScrollView date_scroll = (HorizontalScrollView)getView().findViewById(R.id.date_scroll);
        RadioGroup rg = (RadioGroup) getView().findViewById(R.id.date_radio_group);
        RadioButton rb = (RadioButton) rg.getChildAt(browsingTime.get(Calendar.DATE)-1);
        LogUtil.d("SCROLL",browsingTime.get(Calendar.DATE) + " "+ rb.isChecked());
        if(!rb.isChecked())
            rb.setChecked(true);

        date_scroll.smoothScrollTo(rg.getChildAt(browsingTime.get(Calendar.DATE)-1).getLeft()
                + rg.getChildAt(browsingTime.get(Calendar.DATE)-1).getMeasuredWidth()/2
                + rg.getChildAt(browsingTime.get(Calendar.DATE)-1).getPaddingLeft()
                - date_scroll.getMeasuredWidth()/2, 0);
        LogUtil.d("SCROLL", "autoscrolled");
        LogUtil.d("SCROLL", browsingTime.toString());
    }

    //figure out when the next period is
    private GregorianCalendar getFocusedDate(){

        int endTime = (browsingTime.get(Calendar.DAY_OF_WEEK) == Calendar.WEDNESDAY) ?
                wednesdayPeriodBeginning.get(school)[wednesdayPeriodBeginning.get(school).length-1]
                : regularPeriodBeginning.get(school)[regularPeriodBeginning.get(school).length-1];
        GregorianCalendar temp = new GregorianCalendar();
        temp.setTime(new Date());
        if(new GregorianCalendar().get(Calendar.HOUR_OF_DAY)*100 + new GregorianCalendar().get(Calendar.MINUTE)
                > endTime){
            temp.add(Calendar.DATE, 1);
        }

        int count = 0;
        while(subjectTable.get(temp.get(Calendar.YEAR)+"-"+temp.get(Calendar.MONTH)+"-"+temp.get(Calendar.DATE)) == null && count < 365){
            temp.add(Calendar.DATE, 1);
            count++;
        }

        LogUtil.d("focus_time",temp.toString());

        return temp;
    }

    //append or remove number buttons to fit the new month length
    public void updateTitleBar(){
        View root = getView();
        TextView m = (TextView) root.findViewById(R.id.schedule_month);
        m.setText(month_text());

        RadioGroup rg = (RadioGroup) root.findViewById(R.id.date_radio_group);
        LinearLayout daywk = (LinearLayout) root.findViewById(R.id.days_wk);

        if(month_length(browsingTime) > last_date_length){
            for(int i = last_date_length; i < month_length(browsingTime); i++){
                RadioButton rb = (RadioButton) mActivity.getLayoutInflater().inflate(R.layout.date_button, rg, false);
                rb.setOnClickListener(DATE);
                rb.setText(Integer.toString(i+1));
                rb.setTag(Integer.valueOf(i+1));
                rb.setLayoutParams(date_params);
                rg.addView(rb, i);

            }
        }
        else{
            if(month_length(browsingTime) < last_date_length){
                for(int i = last_date_length - 1; i >= month_length(browsingTime); i--){
                    rg.removeViewAt(i);
                }
            }

        }
        daywk.removeAllViews();
        for(int i=0; i < month_length(browsingTime); i++){
            TextView dwk = (TextView) mActivity.getLayoutInflater().inflate(R.layout.date_wk, null);
            dwk.setText(dayInWeek(browsingTime.get(Calendar.YEAR), browsingTime.get(Calendar.MONTH), i+1).substring(0,1));
            dwk.setLayoutParams(wk_params);
            daywk.addView(dwk);
        }
        //if(browsingTime.date == browsingTime.month_length())
        ((RadioButton)rg.getChildAt(browsingTime.get(Calendar.DATE)-1)).setChecked(true);
        autoscroll();
        last_date_length = month_length(browsingTime);
        for(int i = 0; i < rg.getChildCount(); i++){
            if(((RadioButton)rg.getChildAt(i)).isChecked())
                rg.getChildAt(i).callOnClick();
        }

        setCalendarToMonth();
    }

    /**
     * This method converts dp unit to equivalent pixels, depending on device density.
     *
     * @param dp A value in dp (density independent pixels) unit. Which we need to convert into pixels
     * @param context Context to get resources and device specific display metrics
     * @return A float value to represent px equivalent to dp depending on device density
     */
    public static float convertDpToPixel(float dp, Context context){
        Resources resources = context.getResources();
        DisplayMetrics metrics = resources.getDisplayMetrics();
        return dp * ((float)metrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT);
    }

    //initialize the calendar menu according to the current month --> each month have different #days, so different # of buttons
    public void setCalendarToMonth(){
        int year = browsingTime.get(Calendar.YEAR);
        int month = browsingTime.get(Calendar.MONTH);
        Calendar calendar = Calendar.getInstance();
        calendar.set(year, month, 1);
        calendar.set(Calendar.DATE, calendar.getActualMaximum(Calendar.DATE));
        calendar.set(Calendar.HOUR_OF_DAY, 23);//not sure this is needed

        long endOfMonth = calendar.getTimeInMillis();

        //may need to reinitialize calendar, not sure
        calendar = Calendar.getInstance();
        calendar.set(year, month, 1);
        calendar.set(Calendar.DATE, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        long startOfMonth = calendar.getTimeInMillis();

        cal.removeAllViews();
        expcal = (CalendarView)mActivity.getLayoutInflater().inflate(R.layout.mycalendar, null);
        expcal.setOnDateChangeListener(new CalendarView.OnDateChangeListener(){
            @Override
            public void onSelectedDayChange(CalendarView view, int year, int month, int dayOfMonth){
                if(month!=browsingTime.get(Calendar.MONTH)){
                    browsingTime.set(Calendar.YEAR, year);
                    browsingTime.set(Calendar.MONTH, month);
                    browsingTime.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    updateTitleBar();
                }
                else{
                    browsingTime.set(Calendar.YEAR, year);
                    browsingTime.set(Calendar.MONTH, month);
                    browsingTime.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    autoscroll();
                }
                load();
            }
        });
        cal.addView(expcal);
        expcal.setMaxDate(endOfMonth);
        expcal.setMinDate(startOfMonth);
    }

    private String month_text(){return month_names[browsingTime.get(Calendar.MONTH)];}

    public HashMap<String, Subject[]> getSchedule() throws Exception{
        HashMap<String, Subject[]> schedule = new HashMap<>(6);
        HashMap<String, Integer> pairs             = getDateDayPairs();
        Object[] weeklySchedules = getWeeklySchedule();
        HashMap<Integer, Subject[]> weeklySchedule_sem1 = (HashMap<Integer, Subject[]>)weeklySchedules[0];
        HashMap<Integer, Subject[]> weeklySchedule_sem2 = (HashMap<Integer, Subject[]>)weeklySchedules[1];
        for(Map.Entry<String, Integer> keyValuePair : pairs.entrySet()){
            String date = keyValuePair.getKey();
            Integer day = keyValuePair.getValue();
            HashMap<Integer, Subject[]> weeklySchedule;
            if(day > 18){
                day -= 20;
                weeklySchedule = weeklySchedule_sem2;
            }
            else
                weeklySchedule = weeklySchedule_sem1;
            if(day != -1) schedule.put(date, weeklySchedule.get(day));
            else schedule.put(date, null);
            LogUtil.d("flabberducky",date+" "+day);
        }
        LogUtil.d("SCHEDULE", "got schedule");
        return schedule;
    }

    public HashMap<String, Integer> getDateDayPairs()throws AVException, ParseException, ClassNotFoundException{
        HashMap<String, Integer> pairs;
        try{
            pairs = readDateDayPairs();
            LogUtil.d("CALENDAR", "date-day pairs read success");
        }catch(IOException e) {
            LogUtil.d("CALENDAR", "read failed; using default");
            pairs = defaultDateDayPairs("2019-08-26");
            LogUtil.d("CALENDAR", "date-day pairs defaulted");
        }
        return pairs;
    }

    public HashMap<String, Integer> defaultDateDayPairs(String startOfYear) throws ParseException, AVException {
        HashMap<String, Integer> pairs = new HashMap<>(0);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Calendar c = Calendar.getInstance();
        c.setTime(sdf.parse(startOfYear));
        for(int i = 0; i < 365; i++){
            String date = sdf.format(c.getTime());
            pairs.put(date, -1);
            c.add(Calendar.DATE, 1);
        }
        return pairs;
    }

    public HashMap<String, Integer> readDateDayPairs() throws IOException, ClassNotFoundException{
        FileInputStream f = mActivity.openFileInput("date_day.dat");
        ObjectInputStream s = new ObjectInputStream(f);
        LogUtil.d("CALENDAR", "reading date-day pairs");
        HashMap<String, Integer> dateDay = (HashMap<String, Integer>)s.readObject();
        s.close();
        return dateDay;
    }

    public Object[] getWeeklySchedule(){
        HashMap<Integer, Subject[]> week_sem1;
        HashMap<Integer, Subject[]> week_sem2;
        try{
            week_sem1 = readWeeklySchedule("weeklySchedule_sem1.dat");
            week_sem2 = readWeeklySchedule("weeklySchedule_sem2.dat");
            LogUtil.d("CALENDAR", "read week schedule success");
        }
        catch(Exception e){
            e.printStackTrace();
            week_sem1 = new HashMap<>();
            week_sem2 = new HashMap<>();
            Subject empty = new Subject("-","-","-");
            Subject[] day = {empty, empty, empty, empty, empty, empty};
            for(int i=1; i<=6; i++){
                week_sem1.put(i, day);
                week_sem2.put(i, day);
            }
            LogUtil.d("CALENDAR", "week defaulted");
        }
        Object[] ret = {week_sem1, week_sem2};
        return ret;
    }

    public HashMap<Integer, Subject[]> readWeeklySchedule(String filename) throws IOException{
        FileInputStream f = mActivity.openFileInput(filename);
        BufferedReader in = new BufferedReader(new InputStreamReader(f));
        HashMap<Integer, Subject[]> schedule = new HashMap<>(0);
        LogUtil.d("SCHEDULE", "reading weekly schedule");
        String line;
        int dayInCycle = 1;
        while((line = in.readLine())!=null){
            StringTokenizer tizer = new StringTokenizer(line, "?");

            Subject[] dailySchedule = new Subject[tizer.countTokens()/3];
            for(int period = 0; period < dailySchedule.length; period ++){
                String name = tizer.nextToken();
                String teacher = tizer.nextToken();
                String room = tizer.nextToken();
                Subject subject = new Subject(name, teacher, room);
                dailySchedule[period] = subject;
            }
            schedule.put(dayInCycle, dailySchedule);
            dayInCycle ++;
        }
        in.close();
        LogUtil.d("SCHEDULE", "successfully read weekly schedule");
        return schedule;
    }

    //convert time to day in week
    public static String dayInWeek(int yr, int m, int d) {
        Calendar cal = Calendar.getInstance();
        cal.set(yr, m, d);
        SimpleDateFormat formatter = new SimpleDateFormat("E", Locale.ENGLISH);
        return formatter.format(cal.getTime());
    }
}
