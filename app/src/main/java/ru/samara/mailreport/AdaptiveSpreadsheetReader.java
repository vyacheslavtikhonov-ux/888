package ru.samara.mailreport;
import java.util.*;
import java.util.regex.*;

/** Interprets tables by their contents, synonyms and nearby headings rather than fixed columns. */
final class AdaptiveSpreadsheetReader {
 private static final String[] SAMARA_DISTRICTS={"кировский","железнодорожный","промышленный","ленинский","советский","самарский","октябрьский","красноглинский","куйбышевский"};
 private static final Pattern DATE=Pattern.compile("\\b\\d{1,2}\\.\\d{1,2}(?:\\.\\d{2,4})?\\b");
 private static final Pattern ADDRESS=Pattern.compile("(?iu)(?:ул\\.?|улица|проспект|пр\\.|проезд|пер\\.|переулок|бульвар|шоссе|набережная|просека|дом|д\\.|микрорайон|мкр|квартал|кв\\.|\\d[- ](?:я|й|е)\\s+просека)");
 private static final String[][] KEYS={
  {"address","адрес","место аварии","место происшествия","место инцидента","место отключения","местоположение","объект адрес"},
  {"district","район","внутригородской район","район города"},
  {"municipality","населенн","населённ","муниципал","территория","город","поселение","н.п.","нп "},
  {"houses","кол-во откл","количество отключ","число отключ","кол домов","кол дом","мкд","жилых домов","дома отключ"},
  {"people","кол-во чел","число жител","количество жител","потребител","население","человек","кол чел"},
  {"reason","причина отключ","причина авар","характер и содержание","причины","характер поврежд","описание авар","описание происшествия","причина"},
  {"characteristic","характеристика объекта","характеристика здания","техническая характеристика","тип объекта","характеристика"},
  {"measures","принятые меры","принятые действия","мероприятия","меры по устран","выполненные работы","устранение"},
  {"company","предприятие","исполнитель","ответственная организация","ресурсоснабжающая","организация"},
  {"responsible","ответственный","ответственное лицо","контактный телефон","контакт"},
  {"unit","подразделение","участок организации","филиал"},
  {"service","вид услуг","коммунальная услуга","вид коммунальной","ресурс","вид откл","услуга"},
  {"duration","кол-во час","количество час","продолжительность","перерыв","длительность","длител","продолжител"},
  {"start","начало работ","время начала","дата начала","начало отключ","начало"},
  {"end","окончание работ","время окончания","дата окончания","завершение работ","окончание","восстановление"},
  {"date","дата,","дата заявки","дата происшествия","дата инцидента","дата"},
  {"time","время заявки","время поступ","время"},
  {"status","статус","состояние","текущие работы","ход работ","результат"},
  {"number","№ п/п","номер","№ заявки","п/п"}
 };
 private static String norm(String s){
  if(s==null)return "";
  return s.toLowerCase(Locale.ROOT).replace('ё','е').replace('\u00a0',' ')
     .replaceAll("\\s+"," ").trim();
 }
 private static String v(String[] a,int i){return a==null||i<0||i>=a.length||a[i]==null?"":a[i].trim();}
 private static String field(String[] a,Map<String,Integer> map,String name){
  Integer i=map.get(name);return i==null?"":v(a,i);
 }
 private static String type(String cell) {
  String n=norm(cell);
  if(n.isEmpty())return "";
  for(String[] group:KEYS)for(int i=1;i<group.length;i++){
   String alias=norm(group[i]);
   if(n.equals(alias) || n.contains(alias) && n.length()<105) return group[0];
  }
  return "";
 }
 private static Map<String,Integer> columns(String[] row){
  Map<String,Integer> map=new LinkedHashMap<>();
  for(int i=0;i<row.length;i++){
   String key=type(v(row,i));
   if(!key.isEmpty()&&!map.containsKey(key))map.put(key,i);
  }
  return map;
 }
 private static String service(String input) {
  String t=norm(input);
  if(t.contains("теплоснабжен")||t.contains("отоплен")||t.equals("тсн")||t.equals("т/с"))return "Теплоснабжение";
  if(t.contains("горяч")&&t.contains("вод")||t.equals("гвс"))return "Горячее водоснабжение (ГВС)";
  if(t.contains("холодн")&&t.contains("вод")||t.equals("хвс"))return "Холодное водоснабжение (ХВС)";
  if(t.contains("электроснабжен")||t.contains("электроэнерг")||t.equals("эсн"))return "Электроснабжение";
  if(t.contains("газоснабжен")||t.equals("газ"))return "Газоснабжение";
  if(t.contains("водоотведен")||t.contains("канализац"))return "Водоотведение";
  return "";
 }
 private static boolean samaraDistrict(String s){
  String t=norm(s);for(String name:SAMARA_DISTRICTS)if(t.equals(name))return true;
  return false;
 }
 private static boolean numbered(String s){
  return s.trim().matches("\\d{1,5}(?:[.)])?");
 }
 private static boolean labelLine(String[] row){
  int aliases=0;for(String s:row)if(!type(s).isEmpty())aliases++;
  return aliases>=2;
 }
 private static boolean incidentColumns(Map<String,Integer> map){
  return (map.containsKey("characteristic")||map.containsKey("measures")) &&
    (map.containsKey("address")||map.containsKey("municipality")||map.containsKey("reason"));
 }
 private static boolean operationalColumns(Map<String,Integer> map){
  return (map.containsKey("houses")||map.containsKey("people")||map.containsKey("service")||map.containsKey("duration")) &&
    (map.containsKey("address")||map.containsKey("district")||map.containsKey("reason"));
 }
 private static String approx(String[] row,String first,String second,Map<String,Integer> map){
  String x=field(row,map,first);if(!x.isEmpty())return x;
  return field(row,map,second);
 }
 private static boolean blankOrTotal(String[] row){
  int used=0;StringBuilder combined=new StringBuilder();
  for(String x:row)if(x!=null&&!x.trim().isEmpty()){used++;combined.append(" ").append(norm(x));}
  return used==0||combined.toString().matches(".*\\b(итого|всего по|сумма)\\b.*") && used<7;
 }
 static void scan(ArrayList<String[]> rows,String file,SpreadsheetAttachmentReader.Parsed out){
  Map<String,Integer> headers=new LinkedHashMap<>();
  boolean incident=false, operational=false, samaraContext=false;
  String currentService="", currentTown="",lastContext="";
  int startOps=out.operations.size(),startInc=out.incidents.size();
  for(int rowNum=0;rowNum<rows.size();rowNum++){
   String[] row=rows.get(rowNum);if(row==null)continue;
   if(blankOrTotal(row))continue;
   String joined=String.join(" ",nonNull(row));
   String lower=norm(joined);
   if(lower.contains("г. самары")||lower.contains("город самара")||lower.contains("по самаре"))samaraContext=true;
   if(lower.matches(".*\\b(самарская область|самарской области)\\b.*")&&!lower.contains("г. самары"))samaraContext=false;
   String section=service(v(row,0));
   int filled=0;for(String cell:row)if(cell!=null&&!cell.trim().isEmpty())filled++;
   if(!section.isEmpty() && (lower.length()<100 || filled<=2)){
    currentService=section; continue;
   }
   Map<String,Integer> candidate=columns(row);
   if(operational && candidate.containsKey("start") && candidate.containsKey("end") && !candidate.containsKey("address")){
    headers.put("start",candidate.get("start"));headers.put("end",candidate.get("end"));
    continue;
   }
   if(labelLine(row)&&(incidentColumns(candidate)||operationalColumns(candidate))){
    headers=candidate;incident=incidentColumns(candidate);
    operational=!incident;
    continue;
   }
   if(!headers.isEmpty() && (incident||operational)){
     if(nonData(row,headers))continue;
     if(incident){
       SpreadsheetAttachmentReader.Incident item=new SpreadsheetAttachmentReader.Incident();
       item.source=file;item.address=field(row,headers,"address");
       item.municipality=field(row,headers,"municipality");
       item.reason=field(row,headers,"reason");
       item.kind=incidentKind(item.reason);
       item.description=incidentDescription(item.reason,item.kind);
       item.characteristic=field(row,headers,"characteristic");
       item.measures=field(row,headers,"measures");
       item.unit=approx(row,"unit","company",headers);
       String date=field(row,headers,"date"),time=field(row,headers,"time");
       item.timestamp=(extractDate(date)+" в "+readTime(time)).trim();
       item.applicationNumber=extractApplication(date);
       if(item.municipality.isEmpty()&&samaraDistrict(field(row,headers,"district")))item.municipality="Самара";
       if(item.municipality.isEmpty())item.municipality=currentTown.isEmpty()?"Не указан":currentTown;
       if(!item.address.isEmpty()&&(!item.reason.isEmpty()||!item.characteristic.isEmpty()||!item.measures.isEmpty())){
         out.incidents.add(item);
       }
     }else{
       SpreadsheetAttachmentReader.Operational item=new SpreadsheetAttachmentReader.Operational();
       item.source=file;item.address=field(row,headers,"address");
       item.district=field(row,headers,"district");
       String municipality=field(row,headers,"municipality");
       if(municipality.isEmpty() && samaraDistrict(item.district))municipality="Самара";
       if(municipality.isEmpty())municipality=currentTown;
       if(municipality.isEmpty())municipality=samaraContext?"Самара":"Не указан";
       item.municipality=municipality;
       item.service=service(field(row,headers,"service"));
       if(item.service.isEmpty())item.service=currentService.isEmpty()?"Услуга не указана":currentService;
       item.reason=field(row,headers,"reason");
       item.company=field(row,headers,"company");
       item.responsible=field(row,headers,"responsible");
       item.outageType=field(row,headers,"service");
       item.houses=SpreadsheetAttachmentReader.count(field(row,headers,"houses"));
       item.people=SpreadsheetAttachmentReader.count(field(row,headers,"people"));
       item.duration=SpreadsheetAttachmentReader.hours(field(row,headers,"duration"));
       item.start=field(row,headers,"start");item.end=field(row,headers,"end");
       if(headers.containsKey("start") && headers.containsKey("end")){
         int si=headers.get("start"),ei=headers.get("end");
         if(si+1<row.length&&ei+1<row.length){
          item.start=SpreadsheetAttachmentReader.date(v(row,si),v(row,si+1));
          item.end=SpreadsheetAttachmentReader.date(v(row,ei),v(row,ei+1));
         }
       }
       String rawStatus=field(row,headers,"status");
       String combined=norm(item.reason+" "+rawStatus);
       if(combined.contains("ведутся")||combined.contains("продолжа")||combined.contains("в работе"))item.status="Работы ведутся";
       else if(combined.contains("восстановлен")||combined.contains("завершены"))item.status="Восстановлено (по тексту документа)";
       else item.status="Статус не подтверждён";
       if(!item.address.isEmpty() && (!item.reason.isEmpty()||item.people!=null||item.houses!=null||!item.service.equals("Услуга не указана")))
         out.operations.add(item);
     }
     continue;
   }
   if(lower.matches(".*(город|населенный пункт|населённый пункт|муниципальное образование)\\s*[:\\-].*")){
     int colon=joined.indexOf(':');if(colon>=0)currentTown=joined.substring(colon+1).trim();
   }
  }
  if(out.operations.size()==startOps&&out.incidents.size()==startInc)
    out.warnings.add(file+": структура таблиц не сопоставлена с полями отчёта. Требуется ручная проверка.");
 }
 private static String extractDate(String raw){
  Matcher m=Pattern.compile("\\b\\d{1,2}\\.\\d{1,2}\\.\\d{4}\\b").matcher(raw);
  if(m.find())return m.group();
  return raw.replaceAll(",?\\s*№\\s*\\d+.*$","").trim();
 }
 private static String extractApplication(String raw){
  Matcher m=Pattern.compile("(?iu)№\\s*([0-9]+)").matcher(raw);
  return m.find()?"№ "+m.group(1):"";
 }
 private static String readTime(String raw){
  try{
   double n=Double.parseDouble(raw.replace(',','.'));
   if(n>=0&&n<1){int mins=(int)Math.round(n*1440);return String.format(Locale.ROOT,"%02d:%02d",(mins/60)%24,mins%60);}
  }catch(Exception ignored){}
  return raw.trim();
 }
 private static String incidentKind(String reason){
  String r=norm(reason);
  if(r.startsWith("возгорание"))return "Возгорание";
  if(r.startsWith("нарушение тб в гх"))return "Нарушение ТБ в ГХ";
  if(r.startsWith("утечка"))return "Утечка газа";
  if(r.startsWith("отравление"))return "Отравление";
  if(r.startsWith("пожар"))return "Пожар";
  if(r.startsWith("взрыв"))return "Взрыв";
  int period=reason.indexOf('.');
  if(period>1&&period<85)return reason.substring(0,period).trim();
  int marker=r.indexOf(" обнаружено:");
  if(marker>1&&marker<85)return reason.substring(0,marker).trim();
  return "Не определён";
 }
 private static String incidentDescription(String reason,String kind){
  if(reason==null)return "";
  String t=reason.trim();
  if(!"Не определён".equals(kind) && t.toLowerCase(Locale.ROOT).startsWith(kind.toLowerCase(Locale.ROOT))){
   t=t.substring(kind.length()).replaceFirst("^[\\s.:;\\-]+","").trim();
  }
  return t.isEmpty()?reason.trim():t;
 }
 private static boolean nonData(String[] row,Map<String,Integer> headers){
  if(blankOrTotal(row))return true;
  String addr=field(row,headers,"address");
  if(addr.isEmpty())return true;
  if(norm(addr).equals("адрес")||norm(addr).matches("\\d+"))return true;
  String number=field(row,headers,"number");
  if(!number.isEmpty()&&!numbered(number) && !DATE.matcher(number).find())return true;
  return false;
 }
 private static String[] nonNull(String[] row){
  String[] values=new String[row.length];
  for(int i=0;i<row.length;i++)values[i]=v(row,i);
  return values;
 }
}
