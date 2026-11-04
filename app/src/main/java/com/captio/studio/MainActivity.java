package com.captio.studio;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.text.method.PasswordTransformationMethod;
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
    private final int bg=Color.rgb(16,16,24), card=Color.rgb(30,30,41), muted=Color.rgb(160,160,177), lime=Color.rgb(192,243,107), white=Color.WHITE;
    private Uri videoUri;
    private VideoView video;
    private TextView previewCaption, timeLabel, playButton, status;
    private SeekBar seek;
    private LinearLayout root, list;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ArrayList<Caption> captions=new ArrayList<>();
    private int style=0, position=0, size=0;
    private boolean dragging=false, busy=false;
    private String apiKey="";
    private File rendered;
    private Transformer transformer;
    private ProgressDialog progress;
    private int videoWidth=1080;

    private static class Caption {
        long start, end; String text;
        Caption(long s,long e,String t){start=s;end=e;text=t;}
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
    private TextView button(String s, boolean primary, Runnable action){TextView t=text(s,14,primary?bg:white,true);t.setGravity(Gravity.CENTER);pad(t,16,12);t.setBackground(shape(primary?lime:0xff30303e,14));t.setOnClickListener(v->action.run());return t;}
    private void screen(){
        root=column(); root.setBackgroundColor(bg); root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); pad(root,20,12);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(bg);scroll.addView(root);setContentView(scroll);
    }
    private void home(){
        if(video!=null){video.stopPlayback();video=null;}
        screen();
        LinearLayout top=row(); TextView logo=text("✳",28,lime,true); logo.setGravity(Gravity.CENTER);logo.setBackground(shape(0xff303c25,14));top.addView(logo,lp(48,48));
        LinearLayout brand=column();brand.setPadding(dp(10),0,0,0);brand.addView(text("کپتیو",20,white,true));brand.addView(text("استودیوی زیرنویس هوشمند",11,muted,false));top.addView(brand,new LinearLayout.LayoutParams(0,-2,1));TextView badge=text("نسخه ۱.۰",11,lime,true);badge.setGravity(Gravity.CENTER);pad(badge,10,5);badge.setBackground(outlined(0xff242d20,20,0xff435637));top.addView(badge);root.addView(top);
        gap(root,38);TextView eyebrow=text("✦  از ایده تا انتشار",13,lime,true);root.addView(eyebrow);gap(root,10);
        TextView title=text("حرف‌هات رو\nدیدنی کن.",34,white,true);title.setLineSpacing(dp(3),1f);root.addView(title);gap(root,10);
        TextView desc=text("ویدیو رو انتخاب کن، زیرنویس بساز و با سبک خودت منتشرش کن.",15,muted,false);desc.setLineSpacing(dp(5),1);root.addView(desc);gap(root,25);
        LinearLayout hero=column();pad(hero,18,18);hero.setBackground(outlined(0xff1d1e29,22,0xff333442));
        LinearLayout heroTop=row();TextView icon=text("▶",22,bg,true);icon.setGravity(Gravity.CENTER);icon.setBackground(shape(lime,15));heroTop.addView(icon,lp(54,54));LinearLayout h=column();h.setPadding(dp(12),0,0,0);h.addView(text("پروژهٔ جدید",19,white,true));h.addView(text("همه چیز از یک ویدیو شروع می‌شه",12,muted,false));heroTop.addView(h);hero.addView(heroTop);gap(hero,20);
        TextView select=button("＋  انتخاب ویدیو",true,this::pickVideo);hero.addView(select,lp(-1,-2));gap(hero,10);TextView note=text("ویدیوهای گالری  •  خروجی MP4 و SRT",11,muted,false);note.setGravity(Gravity.CENTER);hero.addView(note);root.addView(hero);
        gap(root,30);root.addView(text("چطور کار می‌کنه؟",17,white,true));gap(root,12);
        feature("۰۱", "ویدیوت رو انتخاب کن", "فایل مورد نظرت رو از گالری باز کن.");feature("۰۲", "زیرنویس رو بساز", "با Whisper متن و زمان‌بندی رو دریافت کن.");feature("۰۳", "شخصی‌سازی و خروجی", "متن، زمان و استایل رو ویرایش کن.");
        gap(root,22);TextView privacy=text("✧  کنترل دست خودته\nبرای رونویسی به کلید OpenAI نیاز داری. صدای ویدیو برای پردازش به این سرویس ارسال می‌شه.",12,muted,false);privacy.setLineSpacing(dp(5),1);pad(privacy,16,14);privacy.setBackground(shape(0xff1a1b24,16));root.addView(privacy);gap(root,20);
    }
    private void feature(String n,String title,String detail){LinearLayout r=row();pad(r,12,10);r.setBackground(shape(0xff191a23,14));TextView num=text(n,14,lime,true);num.setGravity(Gravity.CENTER);num.setBackground(shape(0xff2b3326,10));r.addView(num,lp(40,40));LinearLayout c=column();c.setPadding(dp(12),0,0,0);c.addView(text(title,14,white,true));c.addView(text(detail,12,muted,false));r.addView(c);root.addView(r);gap(root,7);}
    private void pickVideo(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("video/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,PICK_VIDEO);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();
        if(request==PICK_VIDEO){try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}videoUri=uri;captions.clear();style=0;position=0;size=0;readDimensions();editor();}
        else if(request==SAVE_SRT){try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException();out.write(srt().getBytes(StandardCharsets.UTF_8));toast("فایل زیرنویس ذخیره شد");}catch(Exception e){error("ذخیرهٔ زیرنویس انجام نشد.");}}
        else if(request==SAVE_VIDEO && rendered!=null){File source=rendered;showProgress("در حال ذخیرهٔ ویدیو…");new Thread(()->{try(InputStream in=new FileInputStream(source);OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException();byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);source.delete();rendered=null;runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){doneProgress();toast("ویدیو ذخیره شد ✨");}});}catch(Exception e){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){doneProgress();error("ذخیرهٔ ویدیو انجام نشد. دوباره خروجی بگیر.");}});}}).start();}
    }
    private void readDimensions(){MediaMetadataRetriever m=new MediaMetadataRetriever();try{m.setDataSource(this,videoUri);int w=Integer.parseInt(m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));int h=Integer.parseInt(m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));String r=m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);videoWidth=("90".equals(r)||"270".equals(r))?h:w;}catch(Exception e){videoWidth=1080;}finally{try{m.release();}catch(Exception ignored){}}}
    private void editor(){
        if(video!=null){video.stopPlayback();video=null;}
        screen();LinearLayout header=row();TextView back=button("‹",false,this::home);header.addView(back,lp(48,45));LinearLayout head=column();head.setPadding(dp(12),0,0,0);head.addView(text("استودیو",20,white,true));head.addView(text("پروژهٔ جدید",11,muted,false));header.addView(head,new LinearLayout.LayoutParams(0,-2,1));header.addView(button("خروجی ↗",true,this::exportMenu));root.addView(header);gap(root,18);
        FrameLayout frame=new FrameLayout(this);frame.setBackground(shape(Color.BLACK,18));frame.setClipToOutline(true);
        video=new VideoView(this);frame.addView(video,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));video.setVideoURI(videoUri);video.setOnPreparedListener(mp->{mp.setLooping(false);seek.setMax(Math.max(1,video.getDuration()));video.seekTo(1);updatePreview();});video.setOnErrorListener((mp,what,extra)->{error("این ویدیو روی دستگاه قابل پخش نیست.");return true;});
        previewCaption=text("",20,white,true);previewCaption.setGravity(Gravity.CENTER);previewCaption.setMaxLines(4);pad(previewCaption,8,4);FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER_HORIZONTAL|Gravity.BOTTOM);cp.setMargins(dp(20),dp(20),dp(20),dp(30));frame.addView(previewCaption,cp);root.addView(frame,lp(-1,300));gap(root,12);
        LinearLayout controls=row();playButton=button("▶",false,()->{if(video.isPlaying()){video.pause();}else video.start();updatePreview();});controls.addView(playButton,lp(46,42));seek=new SeekBar(this);seek.setMax(1);seek.setProgressTintList(android.content.res.ColorStateList.valueOf(lime));controls.addView(seek,new LinearLayout.LayoutParams(0,dp(42),1));timeLabel=text("۰۰:۰۰ / ۰۰:۰۰",11,muted,false);timeLabel.setGravity(Gravity.CENTER);controls.addView(timeLabel,lp(100,42));root.addView(controls);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){dragging=true;}public void onStopTrackingTouch(SeekBar s){video.seekTo(s.getProgress());dragging=false;updatePreview();}public void onProgressChanged(SeekBar s,int p,boolean user){}});
        gap(root,16);LinearLayout tools=row();TextView generate=button("✦  ساخت زیرنویس",true,this::askKey);tools.addView(generate,new LinearLayout.LayoutParams(0,-2,1));TextView add=button("＋  افزودن دستی",false,()->editCaption(null));LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,-2,1);ap.setMargins(dp(8),0,0,0);tools.addView(add,ap);root.addView(tools);gap(root,24);
        sectionTitle("ظاهر زیرنویس", "استایل دلخواهت رو انتخاب کن");gap(root,12);LinearLayout styles=row();String[] names={"کلاسیک","لیمویی","برجسته","ساده"};for(int i=0;i<names.length;i++){final int x=i;TextView b=text(names[i],12,i==style?bg:white,true);b.setGravity(Gravity.CENTER);pad(b,3,10);b.setBackground(shape(i==style?lime:0xff292a37,12));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);if(i>0)p.setMargins(dp(5),0,0,0);styles.addView(b,p);b.setOnClickListener(v->{style=x;editorAtCurrentTime();});}root.addView(styles);gap(root,10);
        LinearLayout settings=row();settings.addView(button("اندازه: "+new String[]{"متوسط","بزرگ","کوچک"}[size],false,()->{size=(size+1)%3;editorAtCurrentTime();}),new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(0,-2,1);pp.setMargins(dp(8),0,0,0);settings.addView(button("جایگاه: "+new String[]{"پایین","وسط","بالا"}[position],false,()->{position=(position+1)%3;editorAtCurrentTime();}),pp);root.addView(settings);gap(root,25);
        sectionTitle("بخش‌های زیرنویس", "برای ویرایش، هر بخش رو لمس کن");gap(root,12);list=column();root.addView(list);refreshList();gap(root,30);updatePreview();
    }
    private void editorAtCurrentTime(){int ms=video!=null?video.getCurrentPosition():0;editor();video.setOnPreparedListener(mp->{seek.setMax(Math.max(1,video.getDuration()));video.seekTo(ms);updatePreview();});}
    private void sectionTitle(String title,String sub){LinearLayout r=row();LinearLayout c=column();c.addView(text(title,17,white,true));c.addView(text(sub,11,muted,false));r.addView(c,new LinearLayout.LayoutParams(0,-2,1));TextView dot=text("✳",20,lime,true);r.addView(dot);root.addView(r);}
    private void refreshList(){list.removeAllViews();if(captions.isEmpty()){TextView empty=text("هنوز زیرنویسی نداری\nبا ساخت خودکار یا افزودن دستی شروع کن.",13,muted,false);empty.setGravity(Gravity.CENTER);empty.setLineSpacing(dp(5),1);pad(empty,16,24);empty.setBackground(outlined(0xff1a1b24,16,0xff30313d));list.addView(empty);return;}
        captions.sort(Comparator.comparingLong(c->c.start));for(Caption c:captions){LinearLayout item=row();pad(item,14,12);item.setBackground(shape(0xff20212d,14));LinearLayout col=column();TextView times=text(clock(c.start)+"  —  "+clock(c.end),11,lime,true);times.setTextDirection(View.TEXT_DIRECTION_LTR);col.addView(times);TextView content=text(c.text,14,white,true);content.setMaxLines(2);col.addView(content);item.addView(col,new LinearLayout.LayoutParams(0,-2,1));TextView edit=text("✎",20,muted,true);item.addView(edit);item.setOnClickListener(v->{if(video!=null)video.seekTo((int)c.start);editCaption(c);});list.addView(item);gap(list,7);}}
    private void updatePreview(){if(video==null||seek==null)return;int ms=video.getCurrentPosition(),duration=video.getDuration();if(!dragging)seek.setProgress(ms);timeLabel.setText(clock(ms)+" / "+clock(Math.max(duration,0)));playButton.setText(video.isPlaying()?"Ⅱ":"▶");Caption active=null;for(Caption c:captions)if(ms>=c.start&&ms<c.end){active=c;break;}
        previewCaption.setText(active==null?"":active.text);previewCaption.setVisibility(active==null?View.INVISIBLE:View.VISIBLE);previewCaption.setTextColor(style==1?lime:white);previewCaption.setTextSize(size==1?24:size==2?16:20);previewCaption.setBackground(shape(style==3?Color.TRANSPARENT:style==2?0xff6156dc:0xcc111118,8));FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)previewCaption.getLayoutParams();p.gravity=Gravity.CENTER_HORIZONTAL|(position==0?Gravity.BOTTOM:position==1?Gravity.CENTER_VERTICAL:Gravity.TOP);previewCaption.setLayoutParams(p);
    }
    private final Runnable ticker=new Runnable(){public void run(){if(video!=null){updatePreview();handler.postDelayed(this,200);}}};
    @Override protected void onResume(){super.onResume();handler.removeCallbacks(ticker);handler.post(ticker);}
    @Override protected void onPause(){super.onPause();handler.removeCallbacks(ticker);if(video!=null&&video.isPlaying())video.pause();}
    @Override protected void onDestroy(){handler.removeCallbacks(ticker);if(transformer!=null)transformer.cancel();super.onDestroy();}
    private String clock(long ms){return String.format(Locale.US,"%02d:%02d",ms/60000,(ms/1000)%60);}
    private void editCaption(Caption old){LinearLayout form=column();pad(form,20,8);EditText content=new EditText(this);content.setHint("متن زیرنویس");content.setText(old==null?"":old.text);content.setMinLines(2);content.setGravity(Gravity.RIGHT);form.addView(content);LinearLayout times=row();EditText start=new EditText(this),end=new EditText(this);start.setHint("شروع (ثانیه)");end.setHint("پایان (ثانیه)");start.setInputType(8194);end.setInputType(8194);long now=video==null?0:video.getCurrentPosition();start.setText(String.format(Locale.US,"%.2f",(old==null?now:old.start)/1000.0));end.setText(String.format(Locale.US,"%.2f",(old==null?now+3000:old.end)/1000.0));times.addView(start,new LinearLayout.LayoutParams(0,-2,1));times.addView(end,new LinearLayout.LayoutParams(0,-2,1));form.addView(times);TextView hint=text("زمان‌ها بر حسب ثانیه هستند؛ برای اعشار از نقطه استفاده کن.",12,0xff555565,false);form.addView(hint);
        AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle(old==null?"افزودن زیرنویس":"ویرایش زیرنویس").setView(form).setNegativeButton("انصراف",null).setPositiveButton("ذخیره",null);if(old!=null)b.setNeutralButton("حذف",(d,w)->{captions.remove(old);refreshList();updatePreview();});AlertDialog dialog=b.create();if(old!=null){TextView split=button("تقسیم بخش ذخیره‌شده",false,()->{String t=old.text.trim();int mid=t.indexOf(' ',t.length()/2);if(mid<0)mid=t.lastIndexOf(' ',t.length()/2);if(mid<=0||mid>=t.length()-1||old.end-old.start<2){toast("برای تقسیم، متن و زمان بیشتری لازم است.");return;}long halfway=old.start+(old.end-old.start)/2;Caption second=new Caption(halfway,old.end,t.substring(mid+1).trim());old.end=halfway;old.text=t.substring(0,mid).trim();captions.add(second);refreshList();updatePreview();dialog.dismiss();toast("بخش تقسیم شد؛ زمان‌بندی را بررسی کن.");});form.addView(split);}
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(view->{try{String t=content.getText().toString().trim();double a=Double.parseDouble(start.getText().toString()),z=Double.parseDouble(end.getText().toString());if(t.isEmpty()||!Double.isFinite(a)||!Double.isFinite(z)||a<0||z<=a||z>86400||(video!=null&&video.getDuration()>0&&z>video.getDuration()/1000.0+0.1))throw new Exception();if(old==null)captions.add(new Caption((long)(a*1000),(long)(z*1000),t));else{old.start=(long)(a*1000);old.end=(long)(z*1000);old.text=t;}refreshList();updatePreview();dialog.dismiss();}catch(Exception e){toast("متن و زمان معتبر وارد کن؛ پایان باید بعد از شروع و در محدودهٔ ویدیو باشد.");}}));dialog.show();}
    private void askKey(){if(busy)return;LinearLayout form=column();pad(form,20,4);TextView intro=text("صدای ویدیو به OpenAI ارسال می‌شود. استفاده از API ممکن است هزینه داشته باشد. کلید فقط تا پایان این جلسه در حافظه می‌ماند.",14,0xff555565,false);intro.setLineSpacing(dp(4),1);form.addView(intro);EditText key=new EditText(this);key.setSingleLine(true);key.setHint("کلید API");key.setText(apiKey);key.setTransformationMethod(PasswordTransformationMethod.getInstance());key.setTextDirection(View.TEXT_DIRECTION_LTR);form.addView(key);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("ساخت زیرنویس با Whisper").setView(form).setNegativeButton("انصراف",null).setPositiveButton("شروع",null).create();d.setOnShowListener(v->d.getButton(-1).setOnClickListener(view->{String k=key.getText().toString().trim();if(k.isEmpty()){key.setError("کلید را وارد کن");return;}apiKey=k;d.dismiss();if(!captions.isEmpty())new AlertDialog.Builder(this).setMessage("زیرنویس‌های فعلی با نتیجهٔ جدید جایگزین می‌شوند. ادامه می‌دهی؟").setNegativeButton("انصراف",null).setPositiveButton("ادامه",(a,b)->extractAudio()).show();else extractAudio();}));d.show();}
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
        String boundary="Captio"+UUID.randomUUID().toString().replace("-","");conn=(HttpURLConnection)new URL("https://api.openai.com/v1/audio/transcriptions").openConnection();conn.setRequestMethod("POST");conn.setConnectTimeout(30000);conn.setReadTimeout(180000);conn.setDoOutput(true);conn.setRequestProperty("Authorization","Bearer "+apiKey);conn.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary);
        ByteArrayOutputStream prefix=new ByteArrayOutputStream();part(prefix,boundary,"model","whisper-1");part(prefix,boundary,"response_format","verbose_json");prefix.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"audio.m4a\"\r\nContent-Type: audio/mp4\r\n\r\n").getBytes(StandardCharsets.UTF_8));byte[] header=prefix.toByteArray(), footer=("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8);conn.setFixedLengthStreamingMode(header.length+audio.length()+footer.length);
        try(OutputStream out=conn.getOutputStream()){
            out.write(header);try(InputStream in=new FileInputStream(audio)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}out.write(footer);
        }
        int code=conn.getResponseCode();if(code<200||code>=300)throw new IOException("خطای سرویس ("+code+")؛ کلید، اعتبار حساب و اتصال را بررسی کن.");StringBuilder json=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(conn.getInputStream(),StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null)json.append(line);}
        JSONObject result=new JSONObject(json.toString());JSONArray segments=result.optJSONArray("segments");ArrayList<Caption> received=new ArrayList<>();if(segments!=null)for(int i=0;i<segments.length();i++){JSONObject s=segments.getJSONObject(i);long a=(long)(s.getDouble("start")*1000),b=(long)(s.getDouble("end")*1000);String t=s.getString("text").trim();if(!t.isEmpty()&&b>a)received.add(new Caption(a,b,t));}
        runOnUiThread(()->{if(isFinishing()||isDestroyed())return;doneProgress();if(received.isEmpty()){error("گفتاری در صدا پیدا نشد؛ زیرنویس‌های قبلی حفظ شدند.");return;}captions.clear();captions.addAll(received);refreshList();updatePreview();toast("زیرنویس آمادهٔ ویرایش است ✨");});
        }catch(Exception e){String message=e instanceof IOException&&e.getMessage()!=null&&e.getMessage().startsWith("خطای سرویس")?e.getMessage():"رونویسی انجام نشد. اتصال اینترنت و فایل صوتی را بررسی کن.";runOnUiThread(()->{if(isFinishing()||isDestroyed())return;doneProgress();error(message);});}finally{if(conn!=null)conn.disconnect();audio.delete();}}
    private void part(OutputStream out,String boundary,String name,String value)throws IOException{out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+name+"\"\r\n\r\n"+value+"\r\n").getBytes(StandardCharsets.UTF_8));}
    private void exportMenu(){if(busy)return;new AlertDialog.Builder(this).setTitle("خروجی پروژه").setItems(new String[]{"ویدیوی زیرنویس‌دار MP4", "فایل زیرنویس SRT"},(d,which)->{if(which==0)exportVideo();else saveSrt();}).setNegativeButton("انصراف",null).show();}
    private void saveSrt(){if(captions.isEmpty()){toast("ابتدا زیرنویس اضافه کن.");return;}Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/x-subrip");i.putExtra(Intent.EXTRA_TITLE,"captio.srt");startActivityForResult(i,SAVE_SRT);}
    private String timestamp(long ms){return String.format(Locale.US,"%02d:%02d:%02d,%03d",ms/3600000,(ms/60000)%60,(ms/1000)%60,ms%1000);}
    private String srt(){StringBuilder s=new StringBuilder();ArrayList<Caption> sorted=new ArrayList<>(captions);sorted.sort(Comparator.comparingLong(c->c.start));int i=1;for(Caption c:sorted)s.append(i++).append('\n').append(timestamp(c.start)).append(" --> ").append(timestamp(c.end)).append('\n').append(c.text.replace("\r","")).append("\n\n");return s.toString();}
    private void exportVideo(){if(captions.isEmpty()){toast("ابتدا زیرنویس اضافه کن.");return;}if(video!=null&&video.isPlaying())video.pause();showProgress("در حال ساخت ویدیو… لطفاً برنامه را باز نگه دار.");rendered=new File(getCacheDir(),"captio-export.mp4");rendered.delete();
        final ArrayList<Caption> snapshot=new ArrayList<>();for(Caption c:captions)snapshot.add(new Caption(c.start,c.end,c.text));final int chosenStyle=style,chosenPosition=position,chosenSize=size;final int bitmapWidth=Math.max(320,Math.min(1920,(int)(videoWidth*.9f)));final int bitmapHeight=Math.max(160,(int)(bitmapWidth*.36f));final float scale=(videoWidth*.9f)/bitmapWidth;
        BitmapOverlay overlay=new BitmapOverlay(){int last=-2;Bitmap bitmap;
            @Override public Bitmap getBitmap(long us){int index=-1;long ms=us/1000;for(int i=0;i<snapshot.size();i++){Caption c=snapshot.get(i);if(ms>=c.start&&ms<c.end){index=i;break;}}if(index==last&&bitmap!=null)return bitmap;last=index;bitmap=Bitmap.createBitmap(bitmapWidth,bitmapHeight,Bitmap.Config.ARGB_8888);if(index<0)return bitmap;
                Canvas canvas=new Canvas(bitmap);android.text.TextPaint paint=new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);paint.setColor(chosenStyle==1?lime:Color.WHITE);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));paint.setTextSize(bitmapWidth*(chosenSize==1?.072f:chosenSize==2?.045f:.057f));
                String text=snapshot.get(index).text;android.text.StaticLayout layout=android.text.StaticLayout.Builder.obtain(text,0,text.length(),paint,(int)(bitmapWidth*.92f)).setAlignment(android.text.Layout.Alignment.ALIGN_CENTER).setTextDirection(android.text.TextDirectionHeuristics.FIRSTSTRONG_RTL).setIncludePad(true).setMaxLines(4).setEllipsize(TextUtils.TruncateAt.END).build();float y=(bitmapHeight-layout.getHeight())/2f;float x=bitmapWidth*.04f;
                if(chosenStyle!=3){Paint box=new Paint(Paint.ANTI_ALIAS_FLAG);box.setColor(chosenStyle==2?0xff6156dc:0xcc111118);float max=0;for(int i=0;i<layout.getLineCount();i++)max=Math.max(max,layout.getLineWidth(i));float left=Math.max(0,(bitmapWidth-max)/2-bitmapWidth*.025f);canvas.drawRoundRect(left,y-bitmapWidth*.01f,bitmapWidth-left,y+layout.getHeight()+bitmapWidth*.01f,bitmapWidth*.015f,bitmapWidth*.015f,box);}else{paint.setShadowLayer(bitmapWidth*.006f,0,2,Color.BLACK);}
                canvas.save();canvas.translate(x,y);layout.draw(canvas);canvas.restore();return bitmap;}
            @Override public OverlaySettings getOverlaySettings(long us){float y=chosenPosition==0?-.65f:chosenPosition==1?0f:.65f;return new OverlaySettings.Builder().setBackgroundFrameAnchor(0,y).setOverlayFrameAnchor(0,0).setScale(scale,scale).build();}};
        try{Effects effects=new Effects(Collections.emptyList(),Collections.singletonList(new OverlayEffect(Collections.singletonList(overlay))));EditedMediaItem item=new EditedMediaItem.Builder(MediaItem.fromUri(videoUri)).setEffects(effects).build();transformer=new Transformer.Builder(this).addListener(new Transformer.Listener(){@Override public void onCompleted(Composition c,ExportResult r){doneProgress();Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("video/mp4");i.putExtra(Intent.EXTRA_TITLE,"captio-video.mp4");startActivityForResult(i,SAVE_VIDEO);}@Override public void onError(Composition c,ExportResult r,ExportException e){doneProgress();if(rendered!=null)rendered.delete();error("ساخت ویدیو ناموفق بود. ممکن است فرمت فایل یا رمزگذار دستگاه پشتیبانی نشود.");}}).build();transformer.start(item,rendered.getAbsolutePath());}catch(Exception e){doneProgress();if(rendered!=null)rendered.delete();error("شروع خروجی ویدیو ممکن نشد.");}}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void error(String s){if(!isFinishing()&&!isDestroyed())new AlertDialog.Builder(this).setTitle("یه مشکلی پیش اومد").setMessage(s).setPositiveButton("متوجه شدم",null).show();}
}
