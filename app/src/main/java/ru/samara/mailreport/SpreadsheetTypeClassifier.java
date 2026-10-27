package ru.samara.mailreport;

import java.util.ArrayList;
import java.util.Locale;

/** Strict classifier for the two approved mail-attachment workbook types. */
final class SpreadsheetTypeClassifier {
    static final String INCIDENTS = "INCIDENTS";
    static final String OPERATIONAL = "OPERATIONAL";
    static final String OTHER = "OTHER";

    static final class Result {
        final String type;
        final int incidentScore;
        final int operationalScore;
        final String reason;
        Result(String t,int i,int o,String why){
            type=t;incidentScore=i;operationalScore=o;reason=why;
        }
    }

    private static String normalize(String value){
        return (value==null?"":value).toLowerCase(Locale.ROOT)
            .replace('ё','е').replace('\u00a0',' ')
            .replaceAll("[\\r\\n\\t]+"," ")
            .replaceAll("\\s+"," ").trim();
    }

    static Result classify(ArrayList<String[]> rows){
        StringBuilder all=new StringBuilder();
        for(String[] row:rows){
            if(row==null)continue;
            for(String cell:row){
                String value=normalize(cell);
                if(!value.isEmpty())all.append(' ').append(value);
            }
        }
        String t=normalize(all.toString());
        int incident=0,operational=0;

        boolean incidentTitle=has(t,"оперативный журнал учета","журнал учета инцидентов",
            "инцидентов (аварий)","инцидентов аварий");
        boolean incidentCore=has(t,"характер и содержание инцидента",
            "характер и содержание инцидентов","содержание инцидента");
        boolean measures=has(t,"принятые меры","принятых мер");
        boolean characteristic=has(t,"характеристика объекта","характеристики объекта");
        boolean locality=has(t,"населенный пункт","населённый пункт");
        boolean department=has(t,"подразделение");
        boolean application=has(t,"№ заявки","номер заявки","дата, № заявки");

        if(incidentTitle)incident+=3;
        if(incidentCore)incident+=4;
        if(measures)incident+=3;
        if(characteristic)incident+=2;
        if(locality)incident+=1;
        if(department)incident+=1;
        if(application)incident+=1;
        if(has(t,"несчастных случаев"))incident+=1;
        if(has(t,"адрес"))incident+=1;
        boolean incidentEvidence=incidentCore || (incidentTitle&&measures&&characteristic);

        boolean outageHomes=has(t,"кол-во откл дом","количество отключенных домов",
            "количество отключенных домов","кол-во отключ домов","кол откл дом");
        boolean outagePeople=has(t,"кол-во чел","количество человек","количество жителей",
            "кол-во жителей","кол чел");
        boolean outageReason=has(t,"причина отключения","причины отключения");
        boolean duration=has(t,"продолжител. работ","продолжительность работ",
            "длительность перерыва","кол-во час","количество часов","перерыв");
        boolean service=has(t,"вид откл","вид отключения","коммунальная услуга","вид услуги");
        boolean district=has(t,"район");
        boolean company=has(t,"предприятие","ресурсоснабжающая организация");
        boolean responsible=has(t,"ответственный");
        boolean operationalTitle=has(t,"оперативная информация","оперативные сведения",
            "нарушениям режимов работы","жилищном фонде");
        if(outageHomes)operational+=4;
        if(outagePeople)operational+=3;
        if(outageReason)operational+=2;
        if(duration)operational+=2;
        if(service)operational+=2;
        if(district)operational+=1;
        if(company)operational+=1;
        if(responsible)operational+=1;
        if(operationalTitle)operational+=3;
        if(has(t,"теплоснабжение","холодное водоснабжение","горячее водоснабжение"))operational+=1;

        boolean operationalEvidence=(outageHomes||outageReason) &&
            (outagePeople||service||duration);
        if(incidentEvidence && incident>=8 && incident>operational)
            return new Result(INCIDENTS,incident,operational,"Найдены поля журнала инцидентов");
        if(operationalEvidence && operational>=8 && operational>incident)
            return new Result(OPERATIONAL,incident,operational,"Найдены поля оперативных сведений");
        return new Result(OTHER,incident,operational,
            "Структура не соответствует двум поддерживаемым типам");
    }

    private static boolean has(String text,String... terms){
        for(String term:terms)if(text.contains(normalize(term)))return true;
        return false;
    }

    static String label(String type){
        if(INCIDENTS.equals(type))return "Информация по инцидентам";
        if(OPERATIONAL.equals(type))return "Оперативные сведения";
        return "Иной тип файла";
    }
}
