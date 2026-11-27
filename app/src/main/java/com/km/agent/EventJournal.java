package com.km.agent;

import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Privacy-filtered local interaction journal. Never stores message or form contents. */
public final class EventJournal {
    private static final long MAX_FILE_BYTES=2L*1024L*1024L;
    private static final int MAX_LINE_CHARS=4096;
    private final File dir, active, previous;
    public EventJournal(File directory) {
        dir=directory==null?new File("runtime"):directory;
        active=new File(dir,"events.jsonl"); previous=new File(dir,"events.previous.jsonl");
        if(!dir.exists())dir.mkdirs();
    }
    public synchronized void record(String event,String page,JSONObject details) {
        if(!allowed(event))return;
        try {
            if(!dir.exists()&&!dir.mkdirs())return;
            if(active.exists()&&active.length()>=MAX_FILE_BYTES){
                if(previous.exists())previous.delete(); active.renameTo(previous);
            }
            long now=System.currentTimeMillis();
            JSONObject row=new JSONObject().put("timestampMs",now).put("timestamp",format(now))
                    .put("elapsedRealtimeNs",SystemClock.elapsedRealtimeNanos())
                    .put("event",event).put("page",clean(page,64)).put("details",safeDetails(details));
            FileOutputStream out=new FileOutputStream(active,true);
            out.write((row.toString()+"\n").getBytes("UTF-8")); out.close();
        } catch(Exception ignored) { }
    }
    public synchronized JSONArray recent(int requested) {
        int limit=Math.max(1,Math.min(200,requested));
        JSONArray all=new JSONArray(); read(previous,all); read(active,all);
        JSONArray out=new JSONArray(); int start=Math.max(0,all.length()-limit);
        for(int i=start;i<all.length();i++)out.put(all.opt(i));
        return out;
    }
    public synchronized JSONObject runtimeSnapshot(int requested) {
        JSONArray rows=recent(Math.max(8,Math.min(40,requested)));
        JSONArray actions=new JSONArray(); String page="unknown"; long dwell=0L;
        for(int i=0;i<rows.length();i++){
            JSONObject r=rows.optJSONObject(i); if(r==null)continue;
            if(r.optString("page","").length()>0)page=r.optString("page");
            String name=r.optString("event",""); JSONObject d=r.optJSONObject("details");
            if("page.leave".equals(name)&&d!=null)dwell=Math.max(dwell,d.optLong("durationMs",0L));
            if("ui.click".equals(name)||"page.enter".equals(name)||"page.leave".equals(name)
                    ||"app.lifecycle".equals(name)||"attachment.selected".equals(name)){
                JSONObject item=new JSONObject();
                try{item.put("timestamp",r.optString("timestamp","")).put("timestampMs",r.optLong("timestampMs",0L))
                    .put("event",name).put("page",r.optString("page","")); if(d!=null)item.put("details",d); actions.put(item);}
                catch(JSONException ignored){}
            }
        }
        JSONObject result=new JSONObject();
        try{result.put("currentPage",page).put("lastPageDwellMs",dwell).put("eventsIncluded",rows.length())
            .put("timezone",TimeZone.getDefault().getID()).put("locale",Locale.getDefault().toString()).put("recentActions",actions);}
        catch(JSONException ignored){}
        return result;
    }
    public synchronized void clear(){if(active.exists())active.delete();if(previous.exists())previous.delete();}
    private boolean allowed(String e){return "ui.click".equals(e)||"page.enter".equals(e)||"page.leave".equals(e)
        ||"app.lifecycle".equals(e)||"attachment.selected".equals(e);}
    private JSONObject safeDetails(JSONObject source)throws JSONException{
        JSONObject out=new JSONObject(); if(source==null)return out;
        String[] keys={"targetId","label","tag","role","className","durationMs","state","kind"};
        for(String key:keys){Object v=source.opt(key);if(v==null||v==JSONObject.NULL)continue;
            if(v instanceof Number||v instanceof Boolean)out.put(key,v);else out.put(key,clean(String.valueOf(v),80));}
        return out;
    }
    private void read(File f,JSONArray out){
        if(f==null||!f.isFile())return; BufferedReader r=null;
        try{r=new BufferedReader(new InputStreamReader(new FileInputStream(f),"UTF-8"));String line;
            while((line=r.readLine())!=null){if(line.length()==0||line.length()>MAX_LINE_CHARS)continue;try{out.put(new JSONObject(line));}catch(Exception ignored){}}}
        catch(Exception ignored){}finally{if(r!=null)try{r.close();}catch(Exception ignored){}}
    }
    private static String clean(String s,int n){if(s==null)return "";String v=s.replace('\n',' ').replace('\r',' ').trim();return v.length()>n?v.substring(0,n):v;}
    private static String format(long t){return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z",Locale.US).format(new Date(t));}
}
