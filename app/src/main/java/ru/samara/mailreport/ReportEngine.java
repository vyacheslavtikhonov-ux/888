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

    static String build(Date day, ArrayList<Item> emails,
        java.util.List<SpreadsheetAttachmentReader.Operational> operations,
        java.util.List<SpreadsheetAttachmentReader.Incident> incidents,
        ArrayList<String> errors){
        StringBuilder out=new StringBuilder("ОПЕРАТИВНАЯ СВОДКА\n");
        out.append("Дата получения писем: ").append(new SimpleDateFormat("dd.MM.yyyy",new Locale("ru")).format(day))
           .append("\nПроверено писем: ").append(emails.size())
           .append("\nЗаписей об отключениях: ").append(operations.size())
           .append("\nИнцидентов: ").append(incidents.size()).append("\n");
        java.util.HashSet<String> seen=new java.util.HashSet<>();
        for(boolean samara:new boolean[]{true,false}){
            out.append(samara?"\n━━━━ ГОРОД САМАРА ━━━━\n":"\n━━━━ ДРУГИЕ НАСЕЛЁННЫЕ ПУНКТЫ ━━━━\n");
            java.util.LinkedHashMap<String,java.util.ArrayList<SpreadsheetAttachmentReader.Operational>> groups=new java.util.LinkedHashMap<>();
            for(SpreadsheetAttachmentReader.Operational o:operations){
                if(SpreadsheetAttachmentReader.isSamara(o.municipality)!=samara)continue;
                String key=o.source+"|"+o.service+"|"+o.address+"|"+o.reason;
                if(!seen.add(key))continue;
                groups.computeIfAbsent(o.service,k->new java.util.ArrayList<>()).add(o);
            }
            out.append("КОММУНАЛЬНЫЕ УСЛУГИ\n");
            if(groups.isEmpty())out.append("Отключений в приложенных таблицах нет.\n");
            for(java.util.Map.Entry<String,java.util.ArrayList<SpreadsheetAttachmentReader.Operational>> group:groups.entrySet()){
                int homes=0,people=0,missingHomes=0,missingPeople=0;
                for(SpreadsheetAttachmentReader.Operational o:group.getValue()){
                    if(o.houses==null)missingHomes++;else homes+=o.houses;
                    if(o.people==null)missingPeople++;else people+=o.people;
                }
                out.append("\n").append(group.getKey()).append(" — записей: ").append(group.getValue().size())
                   .append(", домов: ").append(homes).append(missingHomes>0?" (без данных: "+missingHomes+")":"")
                   .append(", человек: ").append(people).append(missingPeople>0?" (без данных: "+missingPeople+")":"").append("\n");
                for(SpreadsheetAttachmentReader.Operational o:group.getValue()){
                    out.append("• ").append(o.address).append("\n")
                       .append("  Территория: ").append(o.municipality).append(", ").append(o.district).append("\n")
                       .append("  Причина: ").append(empty(o.reason)).append("\n")
                       .append("  Домов: ").append(o.houses==null?"не указано":o.houses)
                       .append("; человек: ").append(o.people==null?"не указано":o.people).append("\n")
                       .append("  Перерыв: ").append(empty(o.duration)).append("; статус: ").append(empty(o.status)).append("\n")
                       .append("  Начало: ").append(empty(o.start)).append("; окончание: ").append(empty(o.end)).append("\n")
                       .append("  Предприятие: ").append(empty(o.company)).append("; файл: ").append(o.source).append("\n");
                }
            }
            out.append("\nИНЦИДЕНТЫ\n");
            int index=0;
            for(SpreadsheetAttachmentReader.Incident o:incidents){
                if(SpreadsheetAttachmentReader.isSamara(o.municipality)!=samara)continue;
                String key="incident|"+o.source+"|"+o.municipality+"|"+o.address+"|"+o.timestamp;
                if(!seen.add(key))continue;
                out.append(++index).append(". ").append(o.municipality).append(", ").append(empty(o.address))
                   .append("\n  Дата / заявка: ").append(empty(o.timestamp))
                   .append("\n  Причина и описание: ").append(empty(o.reason))
                   .append("\n  Характеристика объекта: ").append(empty(o.characteristic))
                   .append("\n  Принятые меры: ").append(empty(o.measures))
                   .append("\n  Подразделение: ").append(empty(o.unit))
                   .append("\n  Файл: ").append(o.source).append("\n");
            }
            if(index==0)out.append("Инцидентов в приложенных таблицах нет.\n");
        }
        out.append("\n━━━━ ДОПОЛНИТЕЛЬНО: ТЕКСТ ПИСЕМ ━━━━\n");
        out.append(build(day,emails,new ArrayList<String>()));
        if(!errors.isEmpty()){
            out.append("\n\nОШИБКИ ЧТЕНИЯ ВЛОЖЕНИЙ И ПОЧТЫ\n");
            for(String e:errors)out.append("• ").append(e).append("\n");
        }
        out.append("\nПримечание: даты отключений могут отличаться от дат писем; указанное в таблице время окончания не подтверждает фактическое восстановление. Пропущенные числовые значения не приравниваются к нулю.");
        return out.toString();
    }
    private static String empty(String value){return value==null||value.trim().isEmpty()?"не указано":value.trim();}

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
