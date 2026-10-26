package ru.samara.mailreport;

import org.json.JSONArray;
import org.json.JSONObject;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Reads text from already rendered DOM, without reading credentials, cookies or hidden data.
 * Generic webmail cannot guarantee all letters or their dates: only visible rendered rows.
 */
final class BrowserMailCollector {
    static final String SCRIPT = "(function(){try{" +
            "var els=Array.from(document.querySelectorAll('[role=\"row\"],tr,[data-message-id],[data-thread-id],[data-id],.zA,.b-datalist__item,.mail-MessageSnippet,.mail-MessageRow'));" +
            "var seen=new Set();var items=[];" +
            "for(var i=0;i<els.length&&i<1800;i++){" +
            "var el=els[i],txt=((el.innerText||el.getAttribute('aria-label')||'')+'').replace(/\\s+/g,' ').trim();" +
            "if(txt.length<12||txt.length>1200||seen.has(txt))continue;" +
            "if(!/(самар|отключ|авари|ремонт|гвс|отоплен|теплоснабж|водоснабж|поврежден|восстанов|теплов)/i.test(txt))continue;" +
            "seen.add(txt);items.push(txt.slice(0,1000));if(items.length>=500)break;}" +
            "var pw=document.querySelector('input[type=\"password\"]');" +
            "return {ok:true,title:document.title||'',url:location.href,login:!!pw,items:items,visible:document.body?document.body.innerText.length:0};" +
            "}catch(e){return {ok:false,error:String(e),items:[]};}})()";

    static final String SCROLL = "(function(){var all=[document.scrollingElement].concat(Array.from(document.querySelectorAll('*')).filter(function(x){return x.scrollHeight>x.clientHeight+150&&x.clientHeight>100;}));" +
            "all.sort(function(a,b){return (b.scrollHeight-b.clientHeight)-(a.scrollHeight-a.clientHeight);});" +
            "for(var i=0;i<Math.min(all.length,10);i++){var x=all[i];if(x&&x.scrollTop+x.clientHeight<x.scrollHeight-5){x.scrollTop+=Math.max(450,x.clientHeight*0.8);return true;}}" +
            "return false;})()";

    static final class Batch {
        final ArrayList<ReportEngine.Item> matched = new ArrayList<>();
        final ArrayList<String> undated = new ArrayList<>();
        boolean login;
        String url = "";
        String error = "";
        int totalRows;
    }

    private static final Locale RU = new Locale("ru", "RU");
    private static final String[] FORMATS = {"dd.MM.yyyy", "yyyy-MM-dd", "dd/MM/yyyy"};
    private static final java.util.regex.Pattern[] DATES = {
        java.util.regex.Pattern.compile("\\b\\d{1,2}\\.\\d{1,2}\\.\\d{4}\\b"),
        java.util.regex.Pattern.compile("\\b\\d{4}-\\d{1,2}-\\d{1,2}\\b"),
        java.util.regex.Pattern.compile("\\b\\d{1,2}/\\d{1,2}/\\d{4}\\b")
    };

    static Batch parse(String raw, Date selectedDate, String source, Set<String> seen) {
        Batch out = new Batch();
        try {
            JSONObject obj = new JSONObject(raw);
            out.login = obj.optBoolean("login");
            out.url = obj.optString("url");
            if (!obj.optBoolean("ok", true)) out.error = obj.optString("error", "Ошибка чтения страницы");
            JSONArray data = obj.optJSONArray("items");
            if (data == null) return out;
            out.totalRows = data.length();
            for (int i=0;i<data.length();i++) {
                String item = data.optString(i).trim();
                if (item.isEmpty() || !ReportEngine.relevant(item)) continue;
                String dedupeKey = source + "|" + item;
                if (!seen.add(dedupeKey)) continue;
                Date actualDate = dateFromText(item, selectedDate);
                if (actualDate == null) {
                    if (out.undated.size() < 150) out.undated.add(source + ": " + item);
                } else if (sameDate(actualDate,selectedDate)) {
                    out.matched.add(new ReportEngine.Item(source,item,"",source+" (строка веб-почты)",actualDate));
                }
            }
        } catch (Exception ex) { out.error = ex.getMessage(); }
        return out;
    }

    private static Date dateFromText(String text, Date selected) {
        for (int i=0;i<DATES.length;i++) {
            java.util.regex.Matcher m = DATES[i].matcher(text);
            while(m.find()){
                try {
                    SimpleDateFormat fmt = new SimpleDateFormat(FORMATS[i],RU);
                    fmt.setLenient(false);
                    return fmt.parse(m.group());
                } catch(ParseException ignored){}
            }
        }
        String low=text.toLowerCase(RU);
        Calendar day=Calendar.getInstance();
        if(low.contains("сегодня") || low.contains("today")) return day.getTime();
        if(low.contains("вчера") || low.contains("yesterday")) {day.add(Calendar.DATE,-1);return day.getTime();}
        // Short clock times and dates without year are NOT enough to confirm a calendar date.
        return null;
    }

    static boolean sameDate(Date a,Date b){
        Calendar x=Calendar.getInstance();x.setTime(a);
        Calendar y=Calendar.getInstance();y.setTime(b);
        return x.get(Calendar.YEAR)==y.get(Calendar.YEAR)&&x.get(Calendar.DAY_OF_YEAR)==y.get(Calendar.DAY_OF_YEAR);
    }
}
