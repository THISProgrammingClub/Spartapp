package hackthis.team.spartapp;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.Image;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

//this converts a list of data (for class periods) into a list of actual views
//please read the official tutorials and stackoverflow usage ArrayAdapter! it is basically copied from that
//if the 'class Hol' is still confusing, please google 'holders for listview items' or something like that...
public class PeriodAdapter extends ArrayAdapter {

    Context context;

    public PeriodAdapter(Context context, int viewID, List<ClassPeriod> items){
        super(context, viewID, items);
        this.context = context;
    }

    class Hol{
        TextView period;
        TextView num;
        FrameLayout head;
        ImageView image;
        private Hol(TextView NUM, TextView PERIOD, FrameLayout HEAD, ImageView IMAGE){
            num = NUM;
            period = PERIOD;
            head = HEAD;
            image = IMAGE;
        }
    }
    @Override
    public View getView(int position, View convertView, ViewGroup parent){
        ClassPeriod cp = (ClassPeriod) getItem(position); // 获取当前项的实例
        View itemView = null;
        Hol holder;
        if (convertView == null) {
            itemView = View.inflate(context, R.layout.period, null);

            //expand margin between periods to fit screen
            LinearLayout.LayoutParams periodparams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            periodparams.setMargins(0,0,0,50);
            itemView.setLayoutParams(periodparams);
            //
            holder = new Hol(
                    (TextView)itemView.findViewById(R.id.period_num),
                    (TextView)itemView.findViewById(R.id.period_period),
                    (FrameLayout) itemView.findViewById(R.id.period_head),
                    (CircleImageView) itemView.findViewById(R.id.period_image)
            );
            //用setTag的方法把ViewHolder与convertView "绑定"在一起
            itemView.setTag(holder);
        } else {
            //当不为null时，我们让itemView=converView，用getTag方法取出这个itemView对应的holder对象，就可以获取这个itemView对象中的组件
            itemView = convertView;
            holder = (Hol) itemView.getTag();
        }
        //itemView.setOnClickListener(selectDate);

        if(cp.focus) {
            holder.num.setTextColor(context.getResources().getColor(R.color.purple));
            holder.period.setTextColor(context.getResources().getColor(R.color.purple));
            holder.head.setBackgroundColor(context.getResources().getColor(R.color.purple));
            holder.image.setTag(context.getResources().getColor(R.color.purple));
        }
        else{
            holder.num.setTextColor(context.getResources().getColor(R.color.grey));
            holder.period.setTextColor(context.getResources().getColor(R.color.black));
            holder.head.setBackgroundColor(context.getResources().getColor(R.color.shaded_background));
            holder.image.setTag(context.getResources().getColor(R.color.shaded_background));
        }

        holder.image.setImageDrawable(context.getResources().getDrawable(cp.backgroundID));

        String str = (cp.period/2+1) + (cp.period%2==0? "A":"B");
        holder.num.setText(str);
        holder.period.setText(
                TextSize(cp.sub.name+"\n"+cp.sub.teacher+"\n"+cp.sub.room)
        );

        itemView.setMinimumHeight(((Schedule)(((MainActivity) context).schedule)).unitHeight);

        /*
        itemView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                int h = v.getHeight();
                int H = ((Schedule)(((MainActivity) context).schedule)).unitHeight;
                //LogUtil.d("itemview",Integer.toString(H)+"_"+Integer.toString(h));
                //v.setLayoutParams(new LinearLayout.LayoutParams((int)Schedule.convertDpToPixel(10f,context),H>h?H:h));
                h = v.getHeight();
                H = ((Schedule)(((MainActivity) context).schedule)).unitHeight;
                LogUtil.d("itemview",Integer.toString(H)+"_"+Integer.toString(h));
                v.removeOnLayoutChangeListener(this);
            }
        });
        */

        return itemView;
    }

    //this compiles all the texts into styled format
    private SpannableStringBuilder TextSize(String text) {
        RelativeSizeSpan smallSizeText = new RelativeSizeSpan(.7f);
        StyleSpan bold = new StyleSpan(Typeface.BOLD);
        SpannableStringBuilder ssBuilder = new SpannableStringBuilder(text);
        ssBuilder.setSpan(
                smallSizeText,
                text.indexOf("\n"),
                text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        ssBuilder.setSpan(
                bold,
                0,
                text.indexOf("\n"),
                Spanned.SPAN_INCLUSIVE_EXCLUSIVE
        );

        return ssBuilder;
    }
}
