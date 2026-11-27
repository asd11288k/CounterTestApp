package com.km.agent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Declarative plugin registry. Imported packages can only expose vetted built-in tools. */
public final class PluginRegistry {
    private static final Charset UTF8=Charset.forName("UTF-8");
    private static final Pattern ID=Pattern.compile("[a-z0-9][a-z0-9._-]{2,47}");
    private static final String[] ALLOWED={"search_web","fetch_web","read_file","write_file","list_files","now"};
    private final File file; private JSONArray plugins;
    public PluginRegistry(File file){
        this.file=file==null?new File("plugins.json"):file; plugins=load();
        if(plugins.length()==0){
            add("web_research","联网研究","网页搜索与正文读取","实时资料优先用搜索与网页读取工具；保留真实来源，不要把摘要当作原文。",new String[]{"search_web","fetch_web"});
            add("workspace_files","文件与工作区","管理应用私有文本沙箱","文件操作严格限制在 App 私有沙箱，覆盖或删除前必须取得用户确认。",new String[]{"read_file","write_file","list_files"});
            add("time_context","时间与环境","读取当前时间","涉及当前时刻时必须调用 now，不可凭模型记忆猜测。",new String[]{"now"});
            save();
        }
    }
    private JSONArray load(){
        try{if(!file.exists())return new JSONArray();RandomAccessFile f=new RandomAccessFile(file,"r");
            byte[] b=new byte[(int)Math.min(524288,f.length())];f.readFully(b);f.close();return new JSONArray(new String(b,UTF8));}
        catch(Exception e){return new JSONArray();}
    }
    private void add(String id,String name,String desc,String prompt,String[] names){
        try{JSONObject p=new JSONObject().put("id",id).put("name",name).put("description",desc).put("systemPrompt",prompt)
            .put("enabled",true).put("builtin",true).put("version","1.0.0");
            JSONArray a=new JSONArray();for(String n:names)a.put(n);p.put("tools",a);plugins.put(p);}
        catch(JSONException ignored){}
    }
    public synchronized JSONArray list(){try{return new JSONArray(plugins.toString());}catch(Exception e){return new JSONArray();}}
    public synchronized Set<String> enabledToolIds(){
        Set<String> out=new HashSet<String>();
        for(int i=0;i<plugins.length();i++){JSONObject p=plugins.optJSONObject(i);if(p==null||!p.optBoolean("enabled",false))continue;
            JSONArray a=p.optJSONArray("tools");if(a!=null)for(int j=0;j<a.length();j++){String n=a.optString(j,"");if(supported(n))out.add(n);}}
        return out;
    }
    public synchronized JSONArray filterTools(JSONArray source){
        JSONArray out=new JSONArray();Set<String> enabled=enabledToolIds();if(source==null)return out;
        for(int i=0;i<source.length();i++){JSONObject t=source.optJSONObject(i);if(t!=null&&enabled.contains(t.optString("id","")))out.put(t);}return out;
    }
    public synchronized String enabledPromptSections(){
        StringBuilder out=new StringBuilder();
        for(int i=0;i<plugins.length();i++){JSONObject p=plugins.optJSONObject(i);if(p==null||!p.optBoolean("enabled",false))continue;
            String s=p.optString("systemPrompt","").trim();if(s.length()==0)continue;if(out.length()>0)out.append("\n\n");
            out.append("【").append(p.optString("name",p.optString("id"))).append("】\n").append(s);}
        return out.toString();
    }
    public synchronized JSONObject setEnabled(String id,boolean enabled){
        JSONObject p=find(id);if(p==null)return null;try{p.put("enabled",enabled);save();return new JSONObject(p.toString());}catch(Exception e){return null;}
    }
    public synchronized JSONObject install(JSONObject manifest)throws Exception{
        if(manifest==null||manifest.optInt("apiVersion",0)!=1)throw new IllegalArgumentException("插件格式不支持：apiVersion 必须为 1。");
        String id=manifest.optString("id","").trim(),name=manifest.optString("name","").trim(),prompt=manifest.optString("systemPrompt","");
        if(!ID.matcher(id).matches())throw new IllegalArgumentException("插件 id 格式不正确。");
        if(name.length()==0||name.length()>60)throw new IllegalArgumentException("插件名称不能为空或过长。");
        if(prompt.length()>12000)throw new IllegalArgumentException("插件提示词超过 12000 个字符。");
        JSONArray safe=new JSONArray(),raw=manifest.optJSONArray("tools");
        if(raw!=null)for(int i=0;i<raw.length();i++){String n=raw.optString(i,"");if(!supported(n))throw new IllegalArgumentException("插件要求未开放工具："+n);
            boolean exists=false;for(int j=0;j<safe.length();j++)if(n.equals(safe.optString(j)))exists=true;if(!exists)safe.put(n);}
        JSONObject p=find(id);if(p!=null&&p.optBoolean("builtin",false))throw new IllegalArgumentException("不能覆盖内置插件。");
        if(p==null){p=new JSONObject().put("id",id).put("builtin",false);plugins.put(p);}
        p.put("name",name).put("description",manifest.optString("description","")).put("version",manifest.optString("version","1.0.0"))
            .put("systemPrompt",prompt).put("tools",safe).put("enabled",false);save();return new JSONObject(p.toString());
    }
    public synchronized boolean remove(String id){
        JSONArray next=new JSONArray();boolean removed=false;
        for(int i=0;i<plugins.length();i++){JSONObject p=plugins.optJSONObject(i);if(p==null)continue;
            if(id.equals(p.optString("id"))&&!p.optBoolean("builtin",false))removed=true;else next.put(p);}
        if(removed){plugins=next;save();}return removed;
    }
    private JSONObject find(String id){if(id==null)return null;for(int i=0;i<plugins.length();i++){JSONObject p=plugins.optJSONObject(i);if(p!=null&&id.equals(p.optString("id")))return p;}return null;}
    private boolean supported(String id){for(String v:ALLOWED)if(v.equals(id))return true;return false;}
    private void save(){
        try{File parent=file.getParentFile();if(parent!=null&&!parent.exists())parent.mkdirs();File tmp=new File(file.getPath()+".tmp");
            FileOutputStream out=new FileOutputStream(tmp,false);out.write(plugins.toString().getBytes(UTF8));out.close();
            if(file.exists()&&!file.delete())return;tmp.renameTo(file);}catch(Exception ignored){}
    }
}
