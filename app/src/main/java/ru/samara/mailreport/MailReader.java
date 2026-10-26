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
    static final class Attachment {
        final java.io.File file;
        final String filename,mailbox,subject,analysis;
        final boolean analyzed,excel;
        final int outages,incidents;
        Attachment(java.io.File f,String name,String from,String sub,boolean excelFile,
                   boolean ok,int outagesCount,int incidentsCount,String detail){
            file=f;filename=name;mailbox=from;subject=sub;
            excel=excelFile;analyzed=ok;outages=outagesCount;incidents=incidentsCount;analysis=detail;
        }
    }

    static ArrayList<ReportEngine.Item> fetch(AccountStore.Account account,Date day) throws Exception {
        return fetch(account,day,new ArrayList<SpreadsheetAttachmentReader.Operational>(),
            new ArrayList<SpreadsheetAttachmentReader.Incident>(),new ArrayList<String>(),null,null);
    }
    static ArrayList<ReportEngine.Item> fetch(AccountStore.Account account,Date day,
        ArrayList<SpreadsheetAttachmentReader.Operational> operations,
        ArrayList<SpreadsheetAttachmentReader.Incident> incidents,
        ArrayList<String> warnings,
        java.io.File attachmentDirectory,ArrayList<Attachment> attachments) throws Exception {
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
            int limit=messages.length;
            for(int i=0;i<limit;i++){
                Message m=messages[i];String sender=m.getFrom()==null?"Неизвестно":m.getFrom()[0].toString();
                String subject=m.getSubject()==null?"(без темы)":m.getSubject();
                String body="";try{body=extract(m,0);}catch(Exception ignored){body="[Не удалось извлечь тело письма]";}
                if(body.length()>30000)body=body.substring(0,30000);
                list.add(new ReportEngine.Item(account.title,subject,body,sender,m.getReceivedDate()));
                try{
                    collectAttachments(m,0,account.title,subject,day,operations,incidents,warnings,attachmentDirectory,attachments);
                }catch(Exception e){warnings.add(account.title+": ошибка обхода вложений письма «"+subject+"» ("+e.getClass().getSimpleName()+")");}
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
    private static void collectAttachments(Part part,int depth,String mailbox,String subject,Date day,
        ArrayList<SpreadsheetAttachmentReader.Operational> operations,
        ArrayList<SpreadsheetAttachmentReader.Incident> incidents,
        ArrayList<String> warnings,
        java.io.File directory,ArrayList<Attachment> attachments) throws Exception {
        if(depth>10)return;
        String filename=part.getFileName();
        boolean attachment=filename!=null && !filename.trim().isEmpty();
        if(attachment){
            try{
                String original=javax.mail.internet.MimeUtility.decodeText(filename).trim();
                String cleaned=original.replaceAll("[\\\\/:*?\"<>|\\x00-\\x1F]","_");
                if(cleaned.length()>90)cleaned=cleaned.substring(cleaned.length()-90);
                if(cleaned.isEmpty())cleaned="attachment";
                String lower=original.toLowerCase(java.util.Locale.ROOT);
                boolean xlsx=lower.endsWith(".xlsx");
                java.io.ByteArrayOutputStream memory=new java.io.ByteArrayOutputStream();
                java.io.OutputStream target=memory;
                java.io.File destination=null;
                if(directory!=null && attachments!=null){
                    if(!directory.exists()&&!directory.mkdirs())throw new java.io.IOException("Папка вложений недоступна");
                    destination=new java.io.File(directory,java.util.UUID.randomUUID().toString().substring(0,12)+"-"+cleaned);
                    target=new java.io.FileOutputStream(destination);
                }
                long total=0;
                try(java.io.InputStream input=part.getInputStream();java.io.OutputStream output=target){
                    byte[] buffer=new byte[8192];int n;
                    while((n=input.read(buffer))!=-1){
                        total+=n;
                        if(total>18L*1024*1024)throw new java.io.IOException("Вложение больше 18 МБ");
                        output.write(buffer,0,n);
                    }
                }catch(Exception e){if(destination!=null)destination.delete();throw e;}
                boolean analyzed=false;
                int outages=0,incidentsCount=0;
                String analysis="Анализ для этого формата не предусмотрен. Автоматически обрабатываются XLSX-файлы двух поддерживаемых шаблонов.";
                if(xlsx){
                    byte[] bytes=destination!=null?java.nio.file.Files.readAllBytes(destination.toPath()):memory.toByteArray();
                    try{
                        SpreadsheetAttachmentReader.Parsed parsed=SpreadsheetAttachmentReader.parse(bytes,original);
                        operations.addAll(parsed.operations);
                        incidents.addAll(parsed.incidents);
                        for(String message:parsed.warnings)warnings.add(mailbox+": "+message);
                        outages=parsed.operations.size();
                        incidentsCount=parsed.incidents.size();
                        analyzed=outages>0||incidentsCount>0;
                        analysis="Файл: "+original+"\nЯщик: "+mailbox+"\nТема письма: "+subject+"\n\n"+
                            ReportEngine.build(day,new ArrayList<ReportEngine.Item>(),parsed.operations,parsed.incidents,
                                new ArrayList<String>(parsed.warnings));
                    }catch(Exception e){
                        String reason="Не удалось разобрать XLSX «"+original+"»: "+e.getClass().getSimpleName();
                        warnings.add(mailbox+": "+reason);
                        analysis=reason+"\nОткройте исходный файл и проверьте формат.";
                    }
                }else{
                    warnings.add(mailbox+": вложение «"+original+"» сохранено для открытия, но автоматический разбор этого формата пока не поддерживается");
                }
                if(destination!=null)attachments.add(new Attachment(destination,original,mailbox,subject,
                    xlsx,analyzed,outages,incidentsCount,analysis));
            }catch(Exception e){warnings.add(mailbox+": ошибка сохранения вложения «"+filename+"»: "+e.getClass().getSimpleName());}
            return;
        }
        if(part.isMimeType("multipart/*")){
            Multipart multipart=(Multipart)part.getContent();
            for(int i=0;i<multipart.getCount();i++)
                collectAttachments(multipart.getBodyPart(i),depth+1,mailbox,subject,day,operations,incidents,warnings,directory,attachments);
        }else if(part.isMimeType("message/rfc822")&&part.getContent() instanceof Part){
            collectAttachments((Part)part.getContent(),depth+1,mailbox,subject,day,operations,incidents,warnings,directory,attachments);
        }
    }

}
