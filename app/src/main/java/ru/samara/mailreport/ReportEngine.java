package ru.samara.mailreport;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
final class ReportEngine {
    static final class Item {
        String source, subject, body, sender; Date received;
        Item(String source,String subject,String body,String sender,Date received){this.source=source;this.subject=subject;this.body=body;this.sender=sender;this.received=received;}
    }
    private static final Pattern ADDRESS=Pattern.compile("(?iu)(?:г\\.?\\s*самара[,\\s]*)?(?:ул\\.?|улица|пр\\.?|проспект|пер\\.?|переулок|ш\\.?|шоссе|бульвар|б-р|наб\\.?|набережная)\\s+[\\p{L}0-9 .\\-]+?[,\\s]+(?:д\\.?\\s*)?\\d+[\\p{L}\\d/\\-]*");
    private static final String[] EVENT={"отключ", "авари", "поврежден", "порыв", "утечк", "ремонт", "работы", "работ ", "приостанов", "ограничен", "восстанов", "запуск ресурс", "без отоплен", "без гвс", "водоснабжен", "теплоснабжен", "тепловых сет"};
    private static final String[] HEAT={"отоплен", "теплоснабжен", "теплов", "батаре", "подач тепла"};
    private static final String[] WATER={"гвс", "горяч", "водоснабжен", "воды"};
    static String classify(String text){String t=text.toLowerCase(Locale.ROOT);
        if(t.contains("авари")||t.contains("поврежден")||t.contains("порыв")||t.contains("утечк"))return "Авария";
        if(t.contains("восстанов")||t.contains("устранен")||t.contains("возобнов")||t.contains("запущен"))return "Восстановлено";
        if(t.contains("планов")||t.contains("профилакти")||t.contains("регламент"))return "Плановые работы";
        if(t.contains("ремонт")||t.contains("работы")||t.contains("работ "))return "Работы";
        return "Отключение";
    }
    static boolean relevant(String text){String t=text.toLowerCase(Locale.ROOT);
        boolean event=false; for(String word:EVENT)if(t.contains(word)){event=true;break;}
        if(!event)return false;
        return t.contains("самар") || ADDRESS.matcher(text).find();
    }
    static String resource(String text){String t=text.toLowerCase(Locale.ROOT);boolean h=false,w=false;
        for(String s:HEAT)if(t.contains(s)){h=true;break;}
        for(String s:WATER)if(t.contains(s)){w=true;break;}
        return h&&w?"Отопление и ГВС":h?"Отопление":w?"ГВС":"Ресурс не указан";
    }
    static String addresses(String text){Matcher m=ADDRESS.matcher(text);ArrayList<String> a=new ArrayList<>();
        while(m.find() && a.size()<12){String s=m.group().replaceAll("\\s+"," ").trim();if(!a.contains(s))a.add(s);}
        return a.isEmpty()?"Адрес не распознан":String.join("; ",a);
    }
    static String build(Date day, ArrayList<Item> emails, ArrayList<String> errors){
        SimpleDateFormat d=new SimpleDateFormat("dd.MM.yyyy",new Locale("ru"));
        SimpleDateFormat time=new SimpleDateFormat("dd.MM.yyyy HH:mm",new Locale("ru"));
        LinkedHashMap<String,ArrayList<String>> groups=new LinkedHashMap<>();
        for(String key:new String[]{"Авария","Отключение","Плановые работы","Работы","Восстановлено"}) groups.put(key,new ArrayList<>());
        int matches=0; int unknown=0;
        for(Item e:emails){String text=(e.subject==null?"":e.subject)+"\n"+(e.body==null?"":e.body);
            if(!relevant(text))continue;matches++;
            String category=classify(text), addr=addresses(text), res=resource(text);
            if(addr.equals("Адрес не распознан"))unknown++;
            String excerpt=text.replaceAll("\\s+"," ").trim();if(excerpt.length()>240)excerpt=excerpt.substring(0,240)+"…";
            groups.get(category).add("• "+addr+" | "+res+"\n  "+excerpt+"\n  Источник: "+e.source+"; "+e.sender+"; "+(e.received==null?"без даты":time.format(e.received)));
        }
        StringBuilder out=new StringBuilder("ОПЕРАТИВНАЯ СВОДКА ПО Г. САМАРЕ\nДата сообщений: ").append(d.format(day))
                .append("\nПроверено писем: ").append(emails.size()).append("\nТематических сообщений: ").append(matches)
                .append("\nАдрес требует проверки: ").append(unknown).append("\n\n");
        for(Map.Entry<String,ArrayList<String>> g:groups.entrySet()){
            out.append(g.getKey().toUpperCase(new Locale("ru"))).append(" — ").append(g.getValue().size()).append("\n");
            for(String s:g.getValue())out.append(s).append("\n\n");out.append("\n");
        }
        if(!errors.isEmpty()){out.append("ОШИБКИ ПОДКЛЮЧЕНИЯ\n");for(String s:errors)out.append("• ").append(s).append("\n");}
        out.append("\nПримечание: предварительная автоматическая классификация по тексту писем. ")
                .append("Один инцидент может фигурировать в нескольких письмах; данные, адреса и сроки требуют проверки по первоисточнику.");
        return out.toString();
    }
}
