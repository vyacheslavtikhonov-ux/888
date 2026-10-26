package ru.samara.mailreport;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Properties;
import javax.mail.BodyPart;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.search.AndTerm;
import javax.mail.search.ComparisonTerm;
import javax.mail.search.ReceivedDateTerm;
final class MailReader {
    static ArrayList<ReportEngine.Item> fetch(AccountStore.Account account,Date day) throws Exception {
        if(!account.host.matches("[a-zA-Z0-9.\\-]+")||account.port<1||account.port>65535)
            throw new IllegalArgumentException("Неверный IMAP сервер или порт");
        Properties p=new Properties();p.setProperty("mail.store.protocol","imaps");
        p.setProperty("mail.imaps.host",account.host);p.setProperty("mail.imaps.port",String.valueOf(account.port));
        p.setProperty("mail.imaps.ssl.enable","true");p.setProperty("mail.imaps.ssl.checkserveridentity","true");
        p.setProperty("mail.imaps.connectiontimeout","15000");p.setProperty("mail.imaps.timeout","20000");
        Session session=Session.getInstance(p);
        Store store=session.getStore("imaps");Folder folder=null;
        ArrayList<ReportEngine.Item> list=new ArrayList<>();
        try {
            store.connect(account.host,account.port,account.login,account.secret);
            folder=store.getFolder("INBOX");folder.open(Folder.READ_ONLY);
            Calendar start=Calendar.getInstance();start.setTime(day);start.set(Calendar.HOUR_OF_DAY,0);start.set(Calendar.MINUTE,0);start.set(Calendar.SECOND,0);start.set(Calendar.MILLISECOND,0);
            Calendar end=(Calendar)start.clone();end.add(Calendar.DATE,1);
            Message[] messages=folder.search(new AndTerm(
                new ReceivedDateTerm(ComparisonTerm.GE,start.getTime()),
                new ReceivedDateTerm(ComparisonTerm.LT,end.getTime())));
            int limit=Math.min(messages.length,1000);
            for(int i=0;i<limit;i++){
                Message m=messages[i];String sender=m.getFrom()==null?"Неизвестно":m.getFrom()[0].toString();
                String subject=m.getSubject()==null?"(без темы)":m.getSubject();
                String body="";try{body=extract(m,0);}catch(Exception ignored){body="[Не удалось извлечь тело письма]";}
                if(body.length()>30000)body=body.substring(0,30000);
                list.add(new ReportEngine.Item(account.title,subject,body,sender,m.getReceivedDate()));
            }
        } finally {if(folder!=null&&folder.isOpen())try{folder.close(false);}catch(Exception ignored){}
                   if(store.isConnected())try{store.close();}catch(Exception ignored){} }
        return list;
    }
    private static String extract(Part part,int depth) throws Exception {
        if(depth>8)return "";
        if(part.isMimeType("text/plain")){
            Object value=part.getContent();return value instanceof String?(String)value:"";
        }
        if(part.isMimeType("text/html")){
            Object value=part.getContent();if(!(value instanceof String))return "";
            return ((String)value).replaceAll("(?is)<(script|style).*?>.*?</\\1>"," ")
                    .replaceAll("(?s)<[^>]*>"," ").replace("&nbsp;"," ").replace("&amp;","&");
        }
        if(part.isMimeType("multipart/*")){
            Multipart mp=(Multipart)part.getContent();StringBuilder b=new StringBuilder();
            for(int i=0;i<mp.getCount();i++){
                BodyPart child=mp.getBodyPart(i);
                if(Part.ATTACHMENT.equalsIgnoreCase(child.getDisposition()))continue;
                String text=extract(child,depth+1);if(!text.isEmpty())b.append(text).append("\n");
            }return b.toString();
        }
        return "";
    }
}
