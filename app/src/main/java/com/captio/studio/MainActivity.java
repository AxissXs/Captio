package com.captio.studio;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;

import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.BitmapOverlay;
import androidx.media3.effect.OverlayEffect;
import androidx.media3.effect.OverlaySettings;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.Transformer;

import org.json.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

@UnstableApi
public class MainActivity extends Activity {
    private static final int PICK_VIDEO=10, SAVE_SRT=11, SAVE_VIDEO=12;
    private final int bg=0xff111217, card=0xff1c1d23, muted=0xffa5a5ad, lime=0xffff805e, white=Color.WHITE;
    private static final String TRANSCRIPTION_URL="https://api.gapgpt.app/v1/audio/transcriptions";
    private static final String TRANSCRIPTION_MODEL="gapgpt/whisper-1";
    private Uri videoUri;
    private VideoView video;
    private TextView previewCaption, timeLabel, playButton, status;
    private SeekBar seek;
    private LinearLayout root, list;
    private TimelineView timeline;
    private int languageIndex=0;
    private final String[] languageNames={"تشخیص خودکار", "فارسی", "انگلیسی", "عربی", "ترکی", "آلمانی"};
    private final String[] languageCodes={"", "fa", "en", "ar", "tr", "de"};
    private boolean punctuation=true;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ArrayList<Caption> captions=new ArrayList<>();
    private int style=0, position=0, size=0;
    private boolean dragging=false, busy=false;
    private static final String API_KEY="sk-CtLkVCtGVQLuLv7I6MaGsXMpgTmMfrpQGPbmJLzCxh9aLZMZ";
    private File rendered;
    private Transformer transformer;
    private ProgressDialog progress;
    private int videoWidth=1080;

    private static class Caption {
        long start, end; String text;
        final ArrayList<Word> words=new ArrayList<>();
        Caption(long s,long e,String t){start=s;end=e;text=t;}
    }
    private static class Word {
        long start, end; int from, to;
        Word(long start,long end,int from,int to){this.start=start;this.end=end;this.from=from;this.to=to;}
    }
    @Override public void onCreate(Bundle b){super.onCreate(b); getWindow().setStatusBarColor(bg); getWindow().setNavigationBarColor(bg); home();}
    private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private GradientDrawable outlined(int color,int radius,int stroke){GradientDrawable d=shape(color,radius);d.setStroke(dp(1),stroke);return d;}
    private TextView text(String s,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);t.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);t.setIncludeFontPadding(true);if(bold)t.setTypeface(null,1); return t;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private void pad(View v,int h,int vert){v.setPadding(dp(h),dp(vert),dp(h),dp(vert));}
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
    private void gap(LinearLayout l,int height){l.addView(new View(this),lp(1,height));}
    private TextView button(String s, boolean primary, Runnable action){TextView t=text(s,14,white,true);t.setGravity(Gravity.CENTER);pad(t,16,12);t.setBackground(shape(primary?lime:0xff30303e,14));t.setOnClickListener(v->action.run());return t;}
    private void screen(){
        root=column(); root.setBackgroundColor(bg); root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); pad(root,20,12);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(bg);scroll.addView(root);setContentView(scroll);
    }
    private void home(){
        if(video!=null){video.stopPlayback();video=null;}timeline=null;seek=null;previewCaption=null;
        screen();
        LinearLayout header=row();header.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView logo=text("✳",23,white,true);logo.setGravity(Gravity.CENTER);logo.setBackground(shape(lime,14));header.addView(logo,lp(46,46));
        LinearLayout wordmark=column();wordmark.setPadding(dp(10),0,dp(10),0);wordmark.addView(text("کپتیو",20,white,true));wordmark.addView(text("استودیوی زیرنویس",11,muted,false));header.addView(wordmark,new LinearLayout.LayoutParams(0,-2,1));
        TextView badge=text("استودیوی شما",11,0xffffb7a4,true);badge.setGravity(Gravity.CENTER);pad(badge,12,6);badge.setBackground(outlined(0xff2c211f,20,0xff573a34));header.addView(badge);root.addView(header);
        gap(root,32);TextView eyebrow=text("✦   استودیوی ویدیوی تو",12,lime,true);root.addView(eyebrow);gap(root,9);
        TextView headline=text("قصه‌ات رو\nدیدنی کن.",34,white,true);headline.setLineSpacing(dp(2),1);root.addView(headline);gap(root,9);
        TextView intro=text("از یک ویدیو تا محتوایی که همه می‌بینن؛ با زیرنویس‌هایی به سبک خودت.",14,muted,false);intro.setLineSpacing(dp(4),1);root.addView(intro);gap(root,22);
        FrameLayout showcase=new FrameLayout(this);GradientDrawable gradient=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff3d3035,0xff26232f,0xff20232c});gradient.setCornerRadius(dp(24));showcase.setBackground(gradient);showcase.setClipToOutline(true);
        TextView corner=text("✦",48,0x66ff9a7d,false);corner.setGravity(Gravity.CENTER);FrameLayout.LayoutParams cornerP=new FrameLayout.LayoutParams(dp(96),dp(96),Gravity.TOP|Gravity.LEFT);cornerP.setMargins(dp(8),dp(4),0,0);showcase.addView(corner,cornerP);
        TextView number=text("۰۱ / ۰۳",11,0xffc7b9b7,true);FrameLayout.LayoutParams numberP=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.RIGHT);numberP.setMargins(0,dp(18),dp(18),0);showcase.addView(number,numberP);
        LinearLayout sample=column();sample.setGravity(Gravity.CENTER);TextView line=text("کلماتت،",23,white,true);line.setGravity(Gravity.CENTER);sample.addView(line);TextView highlight=text("به سبک خودت",22,white,true);highlight.setGravity(Gravity.CENTER);pad(highlight,12,5);highlight.setBackground(shape(0xff7955c7,10));sample.addView(highlight);TextView sub=text("زیرنویس هوشمند برای ویدیوهای تو",11,0xffd6cfd3,false);sub.setGravity(Gravity.CENTER);gap(sample,10);sample.addView(sub);showcase.addView(sample,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        TextView sampleTag=text("پیش‌نمایش استایل  ✦",10,0xffe0ced0,false);FrameLayout.LayoutParams tagP=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.RIGHT);tagP.setMargins(0,0,dp(18),dp(16));showcase.addView(sampleTag,tagP);root.addView(showcase,lp(-1,185));gap(root,20);
        root.addView(button("＋   انتخاب ویدیو و شروع",true,this::pickVideo),lp(-1,55));gap(root,9);TextView hint=text("ویدیو رو انتخاب کن؛ بقیه‌اش با کپتیو",12,muted,false);hint.setGravity(Gravity.CENTER);root.addView(hint);gap(root,27);
        root.addView(text("از شروع تا انتشار",17,white,true));gap(root,12);
        feature("۰۱","ویدیوت رو انتخاب کن","از گالری، هر چیزی که می‌خوای بسازی.");feature("۰۲","زیرنویس هوشمند بساز","گفتار رو با Whisper به متن تبدیل کن.");feature("۰۳","ویرایش کن و خروجی بگیر","استایل، زمان‌بندی و متن دست خودته.");
        gap(root,16);TextView privacy=text("صدای ویدیو برای رونویسی به GapGPT ارسال می‌شه. برای ساخت زیرنویس، اتصال اینترنت لازمه.",12,muted,false);privacy.setLineSpacing(dp(4),1);pad(privacy,14,12);privacy.setBackground(shape(0xff1b1b22,14));root.addView(privacy);gap(root,15);
    }
    private void feature(String n,String title,String detail){LinearLayout r=row();pad(r,12,10);r.setBackground(shape(0xff191a23,14));TextView num=text(n,14,lime,true);num.setGravity(Gravity.CENTER);num.setBackground(shape(0xff362a28,10));r.addView(num,lp(40,40));LinearLayout c=column();c.setPadding(dp(12),0,0,0);c.addView(text(title,14,white,true));c.addView(text(detail,12,muted,false));r.addView(c);root.addView(r);gap(root,7);}
    private void pickVideo(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("video/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,PICK_VIDEO);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();
        if(request==PICK_VIDEO){try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}videoUri=uri;captions.clear();style=0;position=0;size=0;readDimensions();projectSetup();}
        else if(request==SAVE_SRT){try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException();out.write(srt().getBytes(StandardCharsets.UTF_8));toast("فایل زیرنویس ذخیره شد");}catch(Exception e){error("ذخیرهٔ زیرنویس انجام نشد.");}}
        else if(request==SAVE_VIDEO && rendered!=null){File source=rendered;showProgress("در حال ذخیرهٔ ویدیو…");new Thread(()->{try(InputStream in=new FileInputStream(source);OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException();byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);source.delete();rendered=null;runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){doneProgress();toast("ویدیو ذخیره شد ✨");}});}catch(Exception e){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){doneProgress();error("ذخیرهٔ ویدیو انجام نشد. دوباره خروجی بگیر.");}});}}).start();}
    }
    private void readDimensions(){MediaMetadataRetriever m=new MediaMetadataRetriever();try{m.setDataSource(this,videoUri);int w=Integer.parseInt(m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));int h=Integer.parseInt(m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));String r=m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);videoWidth=("90".equals(r)||"270".equals(r))?h:w;}catch(Exception e){videoWidth=1080;}finally{try{m.release();}catch(Exception ignored){}}}
    /** Project setup deliberately offers only controls that actually affect transcription. */
    private void projectSetup(){
        if(video!=null) video.stopPlayback();
        timeline=null; seek=null; previewCaption=null;
        root=column(); root.setBackgroundColor(bg); root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        setContentView(root);
        FrameLayout frame=new FrameLayout(this); frame.setBackgroundColor(Color.BLACK);
        video=new VideoView(this); frame.addView(video,new FrameLayout.LayoutParams(-1,-1)); video.setVideoURI(videoUri);
        TextView back=overlayButton("✕",this::home); FrameLayout.LayoutParams backP=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP|Gravity.LEFT); backP.setMargins(dp(12),dp(12),0,0); frame.addView(back,backP);
        TextView play=overlayButton("▶",()->{if(video.isPlaying())video.pause();else video.start();}); FrameLayout.LayoutParams playP=new FrameLayout.LayoutParams(dp(72),dp(72),Gravity.CENTER); frame.addView(play,playP);
        video.setOnPreparedListener(mp->{video.seekTo(1);}); video.setOnErrorListener((mp,w,e)->{error("این ویدیو روی دستگاه قابل پخش نیست.");return true;});
        root.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
        ScrollView optionsScroll=new ScrollView(this); optionsScroll.setFillViewport(false); optionsScroll.setBackground(shape(0xff1a1b20,24));
        LinearLayout options=column(); options.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); pad(options,20,16); optionsScroll.addView(options);
        TextView heading=text("پروژهٔ جدید",21,white,true); options.addView(heading); gap(options,4);
        options.addView(text("چند تنظیم ساده قبل از ساخت زیرنویس",12,muted,false)); gap(options,18);
        options.addView(text("زبان ویدیو",13,muted,false));gap(options,7);
        TextView language=optionRow("◎   "+languageNames[languageIndex]+"     ‹"); options.addView(language,lp(-1,52));
        language.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("زبان گفتار").setSingleChoiceItems(languageNames,languageIndex,(d,which)->{languageIndex=which;language.setText("◎   "+languageNames[which]+"     ‹");d.dismiss();}).setNegativeButton("انصراف",null).show());
        gap(options,12); LinearLayout punctuationRow=row(); punctuationRow.setBackground(outlined(0xff1a1b20,14,0xff45454c)); pad(punctuationRow,14,3);
        TextView punctuationLabel=text("نشانه‌گذاری متن",14,white,true); punctuationRow.addView(punctuationLabel,new LinearLayout.LayoutParams(0,-2,1));
        Switch punctuationSwitch=new Switch(this); punctuationSwitch.setChecked(punctuation); punctuationSwitch.setOnCheckedChangeListener((v,checked)->punctuation=checked); punctuationRow.addView(punctuationSwitch); options.addView(punctuationRow,lp(-1,52));
        gap(options,16); options.addView(button("✦   ساخت پروژه و زیرنویس",true,()->{editor();confirmTranscription();}),lp(-1,52));gap(options,8);
        TextView manual=button("ادامه بدون زیرنویس خودکار",false,this::editor);options.addView(manual,lp(-1,45));gap(options,6);
        root.addView(optionsScroll,new LinearLayout.LayoutParams(-1,-2));
    }
    private TextView optionRow(String label){TextView t=text(label,15,white,true);pad(t,14,6);t.setBackground(outlined(0xff1a1b20,14,0xff45454c));return t;}
    private TextView overlayButton(String label,Runnable action){TextView t=text(label,18,white,true);t.setGravity(Gravity.CENTER);t.setBackground(shape(0x99515359,12));t.setOnClickListener(v->action.run());return t;}
    private void editor(){
        if(video!=null) video.stopPlayback();
        root=column(); root.setLayoutDirection(View.LAYOUT_DIRECTION_LTR); root.setBackgroundColor(bg); setContentView(root);
        FrameLayout frame=new FrameLayout(this);frame.setBackground(shape(Color.BLACK,12));frame.setClipToOutline(true);
        video=new VideoView(this);frame.addView(video,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));video.setVideoURI(videoUri);
        previewCaption=text("",20,white,true);previewCaption.setGravity(Gravity.CENTER);previewCaption.setMaxLines(4);pad(previewCaption,8,4);
        FrameLayout.LayoutParams captionParams=new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER_HORIZONTAL|Gravity.BOTTOM);captionParams.setMargins(dp(28),dp(70),dp(28),dp(40));frame.addView(previewCaption,captionParams);
        previewCaption.setOnClickListener(v->{Caption active=activeCaption(video.getCurrentPosition());if(active!=null)editCaption(active);});
        LinearLayout top=row();top.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);top.addView(overlayButton("✕",this::exitEditor),lp(48,45));
        TextView srt=overlayButton("SRT",this::saveSrt);srt.setTextSize(12);LinearLayout.LayoutParams srtP=lp(52,45);srtP.setMargins(dp(7),0,0,0);top.addView(srt,srtP);top.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        TextView export=button("خروجی  ↗",true,this::exportMenu);top.addView(export,lp(105,45));FrameLayout.LayoutParams topP=new FrameLayout.LayoutParams(-1,dp(45),Gravity.TOP);topP.setMargins(dp(10),dp(10),dp(10),0);frame.addView(top,topP);
        LinearLayout rail=column();rail.setGravity(Gravity.CENTER);rail.setBackground(outlined(0x99505055,28,0x66ff9c87));pad(rail,4,5);
        rail.addView(railButton("✦","ساخت",this::confirmTranscription),lp(56,55));rail.addView(railButton("T","متن",()->editCaption(null)),lp(56,55));rail.addView(railButton("◈","استایل",this::chooseStyle),lp(56,55));rail.addView(railButton("⊙","جایگاه",this::cyclePosition),lp(56,55));
        FrameLayout.LayoutParams railP=new FrameLayout.LayoutParams(dp(64),-2,Gravity.LEFT|Gravity.CENTER_VERTICAL);railP.setMargins(dp(8),0,0,0);frame.addView(rail,railP);
        LinearLayout.LayoutParams frameP=new LinearLayout.LayoutParams(-1,0,1);frameP.setMargins(dp(6),dp(6),dp(6),0);root.addView(frame,frameP);
        LinearLayout controls=row();controls.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);pad(controls,12,3);
        playButton=text("▷",26,white,false);playButton.setGravity(Gravity.CENTER);playButton.setOnClickListener(v->{if(video.isPlaying())video.pause();else video.start();updatePreview();});controls.addView(playButton,lp(42,45));
        timeLabel=text("00:00 / 00:00",13,white,false);timeLabel.setTextDirection(View.TEXT_DIRECTION_LTR);timeLabel.setGravity(Gravity.CENTER);controls.addView(timeLabel,lp(100,45));
        seek=new SeekBar(this);seek.setMax(1);seek.setProgressTintList(android.content.res.ColorStateList.valueOf(lime));seek.setThumbTintList(android.content.res.ColorStateList.valueOf(lime));controls.addView(seek,new LinearLayout.LayoutParams(0,dp(42),1));root.addView(controls,lp(-1,50));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){dragging=true;}public void onStopTrackingTouch(SeekBar s){video.seekTo(s.getProgress());dragging=false;updatePreview();}public void onProgressChanged(SeekBar s,int p,boolean user){if(user&&timeline!=null)timeline.invalidate();}});
        timeline=new TimelineView();root.addView(timeline,lp(-1,108));
        HorizontalScrollView toolsScroll=new HorizontalScrollView(this);toolsScroll.setHorizontalScrollBarEnabled(false);toolsScroll.setFillViewport(false);
        LinearLayout tools=row();tools.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);pad(tools,8,4);
        tools.addView(toolButton("✎","ویرایش",this::editCurrent),lp(75,67));tools.addView(toolButton("☰","فهرست",this::showCaptions),lp(75,67));tools.addView(toolButton("＋","افزودن",()->editCaption(null)),lp(75,67));tools.addView(toolButton("◈","استایل",this::chooseStyle),lp(75,67));tools.addView(toolButton("Aa","اندازه",this::cycleSize),lp(75,67));tools.addView(toolButton("⊙","جایگاه",this::cyclePosition),lp(75,67));tools.addView(toolButton("✦","رونویسی",this::confirmTranscription),lp(75,67));toolsScroll.addView(tools);root.addView(toolsScroll,lp(-1,76));
        video.setOnPreparedListener(mp->{mp.setLooping(false);seek.setMax(Math.max(1,video.getDuration()));video.seekTo(1);if(timeline!=null)timeline.loadFrames(videoUri,video.getDuration());updatePreview();});
        video.setOnErrorListener((mp,w,e)->{error("این ویدیو روی دستگاه قابل پخش نیست.");return true;});
        handler.removeCallbacks(ticker);handler.post(ticker);
    }
    private TextView railButton(String icon,String label,Runnable action){TextView t=text(icon+"\n"+label,12,white,false);t.setGravity(Gravity.CENTER);t.setOnClickListener(v->action.run());return t;}
    private TextView toolButton(String icon,String label,Runnable action){TextView t=text(icon+"\n"+label,13,white,false);t.setGravity(Gravity.CENTER);t.setOnClickListener(v->action.run());return t;}
    private void chooseStyle(){String[] styles={"کلاسیک", "طلایی", "بنفش", "ساده"};new AlertDialog.Builder(this).setTitle("استایل زیرنویس").setSingleChoiceItems(styles,style,(d,which)->{style=which;updatePreview();d.dismiss();}).setNegativeButton("بستن",null).show();}
    private void cycleSize(){size=(size+1)%3;updatePreview();toast("اندازه: "+new String[]{"متوسط","بزرگ","کوچک"}[size]);}
    private void cyclePosition(){position=(position+1)%3;updatePreview();toast("جایگاه: "+new String[]{"پایین","وسط","بالا"}[position]);}
    private Caption activeCaption(long ms){for(Caption c:captions)if(ms>=c.start&&ms<c.end)return c;return null;}
    private void editCurrent(){Caption c=activeCaption(video.getCurrentPosition());if(c==null){if(captions.isEmpty())toast("ابتدا زیرنویس بساز یا اضافه کن.");else toast("نشانگر را روی یک بخش زیرنویس ببر یا فهرست را باز کن.");}else editCaption(c);}
    private void showCaptions(){if(captions.isEmpty()){toast("هنوز زیرنویسی وجود ندارد.");return;}ArrayList<Caption> ordered=new ArrayList<>(captions);ordered.sort(Comparator.comparingLong(c->c.start));String[] items=new String[ordered.size()];for(int i=0;i<ordered.size();i++){Caption c=ordered.get(i);items[i]=clock(c.start)+"  ·  "+c.text.replace('\n',' ');}new AlertDialog.Builder(this).setTitle("بخش‌های زیرنویس").setItems(items,(d,which)->{Caption selected=ordered.get(which);video.seekTo((int)selected.start);editCaption(selected);}).setNegativeButton("بستن",null).show();}
    private void exitEditor(){if(busy)return;if(captions.isEmpty()){home();return;}new AlertDialog.Builder(this).setTitle("خروج از پروژه؟").setMessage("تغییرات پروژه ذخیره نمی‌شوند. قبل از خروج، فایل ویدیو یا زیرنویس را خروجی بگیر.").setNegativeButton("ادامهٔ ویرایش",null).setPositiveButton("خروج",(d,w)->home()).show();}
    @Override public void onBackPressed(){if(busy)return;if(video!=null&&seek!=null)exitEditor();else if(video!=null)home();else super.onBackPressed();}
    private void refreshList(){captions.sort(Comparator.comparingLong(c->c.start));if(timeline!=null)timeline.invalidate();}
    private void updatePreview(){
        if(video==null||seek==null||previewCaption==null)return;
        int ms=Math.max(0,video.getCurrentPosition()),duration=Math.max(0,video.getDuration());if(!dragging)seek.setProgress(ms);
        timeLabel.setText(clock(ms)+" / "+clock(duration));playButton.setText(video.isPlaying()?"Ⅱ":"▷");Caption active=activeCaption(ms);
        int activeWord=active==null?-1:wordAt(active,ms);
        previewCaption.setText(active==null?"":styledCaption(active,activeWord));previewCaption.setVisibility(active==null?View.INVISIBLE:View.VISIBLE);
        previewCaption.setTextColor(style==1?0xffffd66d:white);previewCaption.setTextSize(size==1?24:size==2?16:20);previewCaption.setBackground(shape(style==3||(style==2&&active!=null&&!active.words.isEmpty())?Color.TRANSPARENT:style==2?0xff7955c7:0xcc111118,8));
        FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)previewCaption.getLayoutParams();int gravity=Gravity.CENTER_HORIZONTAL|(position==0?Gravity.BOTTOM:position==1?Gravity.CENTER_VERTICAL:Gravity.TOP);if(p.gravity!=gravity){p.gravity=gravity;previewCaption.setLayoutParams(p);}
        if(timeline!=null)timeline.invalidate();
    }
    /** A real, time-proportional filmstrip: tapping a caption opens it; dragging seeks. */
    private class TimelineView extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG); private final ArrayList<Bitmap> frames=new ArrayList<>();
        private float downX,downY;private boolean moved;
        TimelineView(){super(MainActivity.this);setBackgroundColor(0xff18191e);setContentDescription("خط زمان ویدیو و زیرنویس؛ برای جابه‌جایی بکشید");}
        void loadFrames(Uri uri,long duration){TimelineView target=this;new Thread(()->{ArrayList<Bitmap> loaded=new ArrayList<>();MediaMetadataRetriever retriever=new MediaMetadataRetriever();try{retriever.setDataSource(MainActivity.this,uri);for(int i=0;i<8;i++){Bitmap frame=retriever.getFrameAtTime((duration*1000L*i)/8,MediaMetadataRetriever.OPTION_CLOSEST_SYNC);if(frame!=null){Bitmap scaled=Bitmap.createScaledBitmap(frame,dp(65),dp(48),true);if(frame!=scaled)frame.recycle();loaded.add(scaled);}}}catch(Exception ignored){}finally{try{retriever.release();}catch(Exception ignored){}}runOnUiThread(()->{if(timeline!=target){for(Bitmap b:loaded)b.recycle();return;}frames.addAll(loaded);invalidate();});}).start();}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float w=getWidth(), top=dp(7), trackH=dp(38), filmY=dp(52), filmH=dp(48);long duration=video==null?0:Math.max(0,video.getDuration());
            paint.setColor(0xff28292f);canvas.drawRoundRect(0,top,w,top+trackH,dp(8),dp(8),paint);
            if(duration>0)for(Caption c:captions){float left=Math.max(0,w*c.start/duration),right=Math.min(w,w*c.end/duration);if(right<=left)continue;paint.setColor(0xff45464d);canvas.drawRoundRect(left+1,top+1,right-1,top+trackH-1,dp(6),dp(6),paint);canvas.save();canvas.clipRect(left+dp(4),top,right-dp(4),top+trackH);paint.setColor(Color.WHITE);paint.setTextSize(dp(11));paint.setTextAlign(Paint.Align.CENTER);canvas.drawText(c.text.replace('\n',' '),(left+right)/2,top+dp(24),paint);canvas.restore();}
            if(captions.isEmpty()){paint.setColor(0xffa5a5ad);paint.setTextSize(dp(11));paint.setTextAlign(Paint.Align.CENTER);canvas.drawText("زیرنویس‌ها اینجا نمایش داده می‌شوند",w/2,top+dp(24),paint);}
            if(frames.isEmpty()){paint.setColor(0xff303139);canvas.drawRect(0,filmY,w,filmY+filmH,paint);}else for(int i=0;i<frames.size();i++){float l=w*i/frames.size(),r=w*(i+1)/frames.size();canvas.drawBitmap(frames.get(i),null,new RectF(l,filmY,r,filmY+filmH),paint);}
            if(duration>0){float x=w*Math.min(duration,Math.max(0,video.getCurrentPosition()))/duration;paint.setColor(Color.WHITE);paint.setStrokeWidth(dp(2));canvas.drawLine(x,0,x,getHeight(),paint);canvas.drawCircle(x,dp(3),dp(3),paint);}}
        private void seekAt(float x){if(video!=null&&video.getDuration()>0){video.seekTo((int)(Math.max(0,Math.min(getWidth(),x))*video.getDuration()/getWidth()));updatePreview();}}
        @Override public boolean onTouchEvent(android.view.MotionEvent event){switch(event.getAction()){case MotionEvent.ACTION_DOWN:downX=event.getX();downY=event.getY();moved=false;return true;case MotionEvent.ACTION_MOVE:if(Math.abs(event.getX()-downX)>dp(6))moved=true;if(moved)seekAt(event.getX());return true;case MotionEvent.ACTION_UP:if(!moved&&downY<dp(48)&&video!=null&&video.getDuration()>0){long at=(long)(Math.max(0,Math.min(getWidth(),event.getX()))*video.getDuration()/getWidth());Caption c=activeCaption(at);if(c!=null){video.seekTo((int)c.start);editCaption(c);return true;}}seekAt(event.getX());return true;default:return true;}}
    }
    private int wordAt(Caption cue,long ms){for(int i=0;i<cue.words.size();i++){Word w=cue.words.get(i);if(ms>=w.start&&ms<w.end)return i;}return -1;}
    private CharSequence styledCaption(Caption cue,int activeWord){return styledCaption(cue,activeWord,style);}
    private CharSequence styledCaption(Caption cue,int activeWord,int captionStyle){
        if(captionStyle!=2||activeWord<0||activeWord>=cue.words.size())return cue.text;
        Word word=cue.words.get(activeWord);android.text.SpannableString result=new android.text.SpannableString(cue.text);
        if(word.from>=0&&word.to<=result.length()&&word.from<word.to)result.setSpan(new android.text.style.BackgroundColorSpan(0xff7955c7),word.from,word.to,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return result;
    }
    private void attachWordTimings(ArrayList<Caption> cues,JSONArray words,boolean keepPunctuation){
        if(words==null)return;
        for(Caption cue:cues){int offset=0;for(int i=0;i<words.length();i++){JSONObject word=words.optJSONObject(i);if(word==null)continue;
            double start=word.optDouble("start",-1),end=word.optDouble("end",-1);long a=(long)(start*1000),b=(long)(end*1000);
            if(!Double.isFinite(start)||!Double.isFinite(end)||start<0||end<=start||a<cue.start-50||a>=cue.end)continue;
            String value=word.optString("word","").trim();if(!keepPunctuation)value=SubtitleFormatting.withoutPunctuation(value);
            if(value.isEmpty())continue;int at=cue.text.indexOf(value,offset);if(at<0)continue;cue.words.add(new Word(a,b,at,at+value.length()));offset=at+value.length();
        }}
    }
    private final Runnable ticker=new Runnable(){public void run(){if(video!=null){updatePreview();handler.postDelayed(this,200);}}};
    @Override protected void onResume(){super.onResume();handler.removeCallbacks(ticker);handler.post(ticker);}
    @Override protected void onPause(){super.onPause();handler.removeCallbacks(ticker);if(video!=null&&video.isPlaying())video.pause();}
    @Override protected void onDestroy(){handler.removeCallbacks(ticker);if(transformer!=null)transformer.cancel();super.onDestroy();}
    private String clock(long ms){return String.format(Locale.US,"%02d:%02d",ms/60000,(ms/1000)%60);}
    private void editCaption(Caption old){LinearLayout form=column();pad(form,20,8);EditText content=new EditText(this);content.setHint("متن زیرنویس");content.setText(old==null?"":old.text);content.setMinLines(2);content.setGravity(Gravity.RIGHT);form.addView(content);LinearLayout times=row();EditText start=new EditText(this),end=new EditText(this);start.setHint("شروع (ثانیه)");end.setHint("پایان (ثانیه)");start.setInputType(8194);end.setInputType(8194);long now=video==null?0:video.getCurrentPosition();long duration=video==null?0:video.getDuration();if(old==null&&duration>0&&now>=duration)now=Math.max(0,duration-3000);long defaultEnd=duration>0?Math.min(duration,now+3000):now+3000;start.setText(String.format(Locale.US,"%.2f",(old==null?now:old.start)/1000.0));end.setText(String.format(Locale.US,"%.2f",(old==null?defaultEnd:old.end)/1000.0));times.addView(start,new LinearLayout.LayoutParams(0,-2,1));times.addView(end,new LinearLayout.LayoutParams(0,-2,1));form.addView(times);TextView hint=text("زمان‌ها بر حسب ثانیه هستند؛ برای اعشار از نقطه استفاده کن.",12,muted,false);form.addView(hint);
        AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle(old==null?"افزودن زیرنویس":"ویرایش زیرنویس").setView(form).setNegativeButton("انصراف",null).setPositiveButton("ذخیره",null);if(old!=null)b.setNeutralButton("حذف",(d,w)->{captions.remove(old);refreshList();updatePreview();});AlertDialog dialog=b.create();if(old!=null){TextView split=button("تقسیم بخش ذخیره‌شده",false,()->{String t=old.text.trim();int mid=t.indexOf(' ',t.length()/2);if(mid<0)mid=t.lastIndexOf(' ',t.length()/2);if(mid<=0||mid>=t.length()-1||old.end-old.start<2){toast("برای تقسیم، متن و زمان بیشتری لازم است.");return;}long halfway=old.start+(old.end-old.start)/2;Caption second=new Caption(halfway,old.end,t.substring(mid+1).trim());old.end=halfway;old.text=t.substring(0,mid).trim();old.words.clear();captions.add(second);refreshList();updatePreview();dialog.dismiss();toast("بخش تقسیم شد؛ زمان‌بندی را بررسی کن.");});form.addView(split);}
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(view->{try{String t=content.getText().toString().trim();double a=Double.parseDouble(start.getText().toString()),z=Double.parseDouble(end.getText().toString());if(t.isEmpty()||!Double.isFinite(a)||!Double.isFinite(z)||a<0||z<=a||z>86400||(video!=null&&video.getDuration()>0&&z>video.getDuration()/1000.0+0.1))throw new Exception();if(old==null)captions.add(new Caption((long)(a*1000),(long)(z*1000),t));else{old.start=(long)(a*1000);old.end=(long)(z*1000);old.text=t;old.words.clear();}refreshList();updatePreview();dialog.dismiss();}catch(Exception e){toast("متن و زمان معتبر وارد کن؛ پایان باید بعد از شروع و در محدودهٔ ویدیو باشد.");}}));dialog.show();}
    private void confirmTranscription(){
        if(busy)return;
        String message="صدای ویدیو برای ساخت زیرنویس به GapGPT ارسال می‌شود. استفاده از سرویس ممکن است هزینه داشته باشد.";
        if(!captions.isEmpty())message+="\n\nزیرنویس‌های فعلی فقط پس از دریافت نتیجهٔ جدید جایگزین می‌شوند.";
        new AlertDialog.Builder(this).setTitle("ساخت زیرنویس با Whisper").setMessage(message)
            .setNegativeButton("انصراف",null).setPositiveButton("شروع",(dialog,which)->extractAudio()).show();
    }
    private void showProgress(String message){busy=true;progress=new ProgressDialog(this);progress.setMessage(message);progress.setIndeterminate(true);progress.setCancelable(false);progress.show();}
    private void doneProgress(){busy=false;if(progress!=null){progress.dismiss();progress=null;}transformer=null;}
    private void extractAudio(){
        if(videoUri==null||busy)return;if(video!=null&&video.isPlaying())video.pause();showProgress("در حال جدا کردن صدا از ویدیو…");File audio=new File(getCacheDir(),"whisper-audio.m4a");audio.delete();
        try{transformer=new Transformer.Builder(this).addListener(new Transformer.Listener(){@Override public void onCompleted(Composition c,ExportResult r){transformer=null;if(!audio.exists()||audio.length()==0){doneProgress();audio.delete();error("صدایی در ویدیو پیدا نشد.");return;}if(audio.length()>25_000_000){doneProgress();audio.delete();error("فایل صوتی بیشتر از ۲۵ مگابایت است. ویدیوی کوتاه‌تری انتخاب کن.");return;}if(progress!=null)progress.setMessage("در حال ارسال صدا و دریافت زیرنویس…");new Thread(()->transcribe(audio)).start();}
            @Override public void onError(Composition c,ExportResult r,ExportException e){doneProgress();audio.delete();error("استخراج صدا ممکن نشد. مطمئن شو ویدیو دارای صدای قابل پخش است.");}}).build();
            EditedMediaItem item=new EditedMediaItem.Builder(MediaItem.fromUri(videoUri)).setRemoveVideo(true).build();transformer.start(item,audio.getAbsolutePath());
        }catch(Exception e){doneProgress();audio.delete();error("شروع استخراج صدا ممکن نشد.");}
    }
    private void transcribe(File audio){HttpURLConnection conn=null;try{
        String boundary="Captio"+UUID.randomUUID().toString().replace("-","");conn=(HttpURLConnection)new URL(TRANSCRIPTION_URL).openConnection();conn.setRequestMethod("POST");conn.setInstanceFollowRedirects(false);conn.setConnectTimeout(30000);conn.setReadTimeout(180000);conn.setDoOutput(true);conn.setRequestProperty("Authorization","Bearer "+API_KEY);conn.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary);
        ByteArrayOutputStream prefix=new ByteArrayOutputStream();part(prefix,boundary,"model",TRANSCRIPTION_MODEL);part(prefix,boundary,"response_format","verbose_json");part(prefix,boundary,"timestamp_granularities[]","segment");part(prefix,boundary,"timestamp_granularities[]","word");if(!languageCodes[languageIndex].isEmpty())part(prefix,boundary,"language",languageCodes[languageIndex]);prefix.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"audio.m4a\"\r\nContent-Type: audio/mp4\r\n\r\n").getBytes(StandardCharsets.UTF_8));byte[] header=prefix.toByteArray(), footer=("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8);conn.setFixedLengthStreamingMode(header.length+audio.length()+footer.length);
        try(OutputStream out=conn.getOutputStream()){
            out.write(header);try(InputStream in=new FileInputStream(audio)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}out.write(footer);
        }
        int code=conn.getResponseCode();if(code<200||code>=300)throw new IOException("خطای سرویس ("+code+")؛ کلید، اعتبار حساب و اتصال را بررسی کن.");StringBuilder json=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(conn.getInputStream(),StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null)json.append(line);}
        JSONObject result=new JSONObject(json.toString());JSONArray segments=result.optJSONArray("segments");if(segments==null)throw new IOException("پاسخ سرویس زمان‌بندی بخش‌ها را ندارد؛ ساخت زیرنویس ممکن نیست.");ArrayList<Caption> received=new ArrayList<>();if(segments!=null)for(int i=0;i<segments.length();i++){JSONObject s=segments.getJSONObject(i);double start=s.getDouble("start"),end=s.getDouble("end");if(!Double.isFinite(start)||!Double.isFinite(end)||start<0||end<=start||end>86400)continue;long a=(long)(start*1000),b=(long)(end*1000);String t=s.getString("text").trim();if(!punctuation)t=SubtitleFormatting.withoutPunctuation(t);if(!t.isEmpty()&&b>a)received.add(new Caption(a,b,t));}
        attachWordTimings(received,result.optJSONArray("words"),punctuation);
        runOnUiThread(()->{if(isFinishing()||isDestroyed())return;doneProgress();if(received.isEmpty()){error("گفتاری در صدا پیدا نشد؛ زیرنویس‌های قبلی حفظ شدند.");return;}captions.clear();captions.addAll(received);refreshList();updatePreview();toast("زیرنویس آمادهٔ ویرایش است ✨");});
        }catch(Exception e){String message=e instanceof IOException&&e.getMessage()!=null&&(e.getMessage().startsWith("خطای سرویس")||e.getMessage().startsWith("پاسخ سرویس"))?e.getMessage():"رونویسی انجام نشد. اتصال اینترنت و فایل صوتی را بررسی کن.";runOnUiThread(()->{if(isFinishing()||isDestroyed())return;doneProgress();error(message);});}finally{if(conn!=null)conn.disconnect();audio.delete();}}
    private void part(OutputStream out,String boundary,String name,String value)throws IOException{out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+name+"\"\r\n\r\n"+value+"\r\n").getBytes(StandardCharsets.UTF_8));}
    private void exportMenu(){if(busy)return;new AlertDialog.Builder(this).setTitle("خروجی پروژه").setItems(new String[]{"ویدیوی زیرنویس‌دار MP4", "فایل زیرنویس SRT"},(d,which)->{if(which==0)exportVideo();else saveSrt();}).setNegativeButton("انصراف",null).show();}
    private void saveSrt(){if(captions.isEmpty()){toast("ابتدا زیرنویس اضافه کن.");return;}Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/x-subrip");i.putExtra(Intent.EXTRA_TITLE,"captio.srt");startActivityForResult(i,SAVE_SRT);}
    private String timestamp(long ms){return SubtitleFormatting.srtTimestamp(ms);}
    private String srt(){StringBuilder s=new StringBuilder();ArrayList<Caption> sorted=new ArrayList<>(captions);sorted.sort(Comparator.comparingLong(c->c.start));int i=1;for(Caption c:sorted)s.append(i++).append('\n').append(timestamp(c.start)).append(" --> ").append(timestamp(c.end)).append('\n').append(c.text.replace("\r","")).append("\n\n");return s.toString();}
    private void exportVideo(){if(captions.isEmpty()){toast("ابتدا زیرنویس اضافه کن.");return;}if(video!=null&&video.isPlaying())video.pause();showProgress("در حال ساخت ویدیو… لطفاً برنامه را باز نگه دار.");rendered=new File(getCacheDir(),"captio-export.mp4");rendered.delete();
        final ArrayList<Caption> snapshot=new ArrayList<>();for(Caption c:captions){Caption copy=new Caption(c.start,c.end,c.text);copy.words.addAll(c.words);snapshot.add(copy);}final int chosenStyle=style,chosenPosition=position,chosenSize=size;final int bitmapWidth=Math.max(320,Math.min(1920,(int)(videoWidth*.9f)));final int bitmapHeight=Math.max(160,(int)(bitmapWidth*.36f));final float scale=(videoWidth*.9f)/bitmapWidth;
        BitmapOverlay overlay=new BitmapOverlay(){int last=-2,lastWord=-2;Bitmap bitmap;
            @Override public Bitmap getBitmap(long us){int index=-1;long ms=us/1000;for(int i=0;i<snapshot.size();i++){Caption c=snapshot.get(i);if(ms>=c.start&&ms<c.end){index=i;break;}}int word=index<0?-1:wordAt(snapshot.get(index),ms);if(index==last&&word==lastWord&&bitmap!=null)return bitmap;last=index;lastWord=word;if(bitmap==null)bitmap=Bitmap.createBitmap(bitmapWidth,bitmapHeight,Bitmap.Config.ARGB_8888);else bitmap.eraseColor(Color.TRANSPARENT);if(index<0)return bitmap;
                Canvas canvas=new Canvas(bitmap);android.text.TextPaint paint=new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);paint.setColor(chosenStyle==1?0xffffd66d:Color.WHITE);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));paint.setTextSize(bitmapWidth*(chosenSize==1?.072f:chosenSize==2?.045f:.057f));
                Caption cue=snapshot.get(index);CharSequence text=chosenStyle==2?styledCaption(cue,word,chosenStyle):cue.text;android.text.StaticLayout layout=android.text.StaticLayout.Builder.obtain(text,0,text.length(),paint,(int)(bitmapWidth*.92f)).setAlignment(android.text.Layout.Alignment.ALIGN_CENTER).setTextDirection(android.text.TextDirectionHeuristics.FIRSTSTRONG_RTL).setIncludePad(true).setMaxLines(4).setEllipsize(TextUtils.TruncateAt.END).build();float y=(bitmapHeight-layout.getHeight())/2f;float x=bitmapWidth*.04f;
                if(chosenStyle!=3&&!(chosenStyle==2&&!cue.words.isEmpty())){Paint box=new Paint(Paint.ANTI_ALIAS_FLAG);box.setColor(chosenStyle==2?0xff7955c7:0xcc111118);float max=0;for(int i=0;i<layout.getLineCount();i++)max=Math.max(max,layout.getLineWidth(i));float left=Math.max(0,(bitmapWidth-max)/2-bitmapWidth*.025f);canvas.drawRoundRect(left,y-bitmapWidth*.01f,bitmapWidth-left,y+layout.getHeight()+bitmapWidth*.01f,bitmapWidth*.015f,bitmapWidth*.015f,box);}else{paint.setShadowLayer(bitmapWidth*.006f,0,2,Color.BLACK);}
                canvas.save();canvas.translate(x,y);layout.draw(canvas);canvas.restore();return bitmap;}
            @Override public OverlaySettings getOverlaySettings(long us){float y=chosenPosition==0?-.65f:chosenPosition==1?0f:.65f;return new OverlaySettings.Builder().setBackgroundFrameAnchor(0,y).setOverlayFrameAnchor(0,0).setScale(scale,scale).build();}};
        try{Effects effects=new Effects(Collections.emptyList(),Collections.singletonList(new OverlayEffect(Collections.singletonList(overlay))));EditedMediaItem item=new EditedMediaItem.Builder(MediaItem.fromUri(videoUri)).setEffects(effects).build();transformer=new Transformer.Builder(this).addListener(new Transformer.Listener(){@Override public void onCompleted(Composition c,ExportResult r){doneProgress();Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("video/mp4");i.putExtra(Intent.EXTRA_TITLE,"captio-video.mp4");startActivityForResult(i,SAVE_VIDEO);}@Override public void onError(Composition c,ExportResult r,ExportException e){doneProgress();if(rendered!=null)rendered.delete();error("ساخت ویدیو ناموفق بود. ممکن است فرمت فایل یا رمزگذار دستگاه پشتیبانی نشود.");}}).build();transformer.start(item,rendered.getAbsolutePath());}catch(Exception e){doneProgress();if(rendered!=null)rendered.delete();error("شروع خروجی ویدیو ممکن نشد.");}}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void error(String s){if(!isFinishing()&&!isDestroyed())new AlertDialog.Builder(this).setTitle("یه مشکلی پیش اومد").setMessage(s).setPositiveButton("متوجه شدم",null).show();}
}
