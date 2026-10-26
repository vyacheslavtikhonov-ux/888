package ru.samara.mailreport;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;

/** Read only XLSX cell data; no external Office libraries required. */
final class SpreadsheetAttachmentReader {
 static final class Operational {
  String source,municipality,district,service,address,reason,company,responsible,outageType,start,end,duration,status;
  Integer houses,people;
 }
 static final class Incident {
  String source,municipality,address,timestamp,applicationNumber,unit,characteristic,reason,kind,description,measures;
 }
 static final class Parsed {
  final ArrayList<Operational> operations=new ArrayList<>();
  final ArrayList<Incident> incidents=new ArrayList<>();
  final ArrayList<String> warnings=new ArrayList<>();
 }
 private static final int LIMIT=18*1024*1024;
 static Parsed parse(byte[] raw,String file) throws Exception {
  if(raw==null||raw.length==0||raw.length>LIMIT)throw new IOException("Слишком большое или пустое XLSX-вложение");
  Map<String,byte[]> zip=unzip(raw);
  ArrayList<String> shared=sharedStrings(zip.get("xl/sharedStrings.xml"));
  ArrayList<String> sheets=new ArrayList<>();
  for(String k:zip.keySet())if(k.matches("xl/worksheets/sheet[0-9]+\\.xml"))sheets.add(k);
  Collections.sort(sheets);
  Parsed parsed=new Parsed();
  for(String s:sheets){
   ArrayList<String[]> rows=rows(zip.get(s),shared);
   AdaptiveSpreadsheetReader.scan(rows,file,parsed);
  }
  if(parsed.operations.isEmpty()&&parsed.incidents.isEmpty())parsed.warnings.add(file+": значимые данные не распознаны, проверьте структуру таблицы");
  return parsed;
 }
 static Map<String,byte[]> unzip(byte[] data) throws Exception {
  LinkedHashMap<String,byte[]> found=new LinkedHashMap<>();
  int total=0,entries=0;
  try(ZipInputStream z=new ZipInputStream(new ByteArrayInputStream(data))){
   ZipEntry e;byte[] b=new byte[8192];
   while((e=z.getNextEntry())!=null){
    if(++entries>350)throw new IOException("Превышен лимит файлов в XLSX");
    String name=e.getName();
    boolean keep=name.equals("xl/sharedStrings.xml")||name.matches("xl/worksheets/sheet[0-9]+\\.xml");
    ByteArrayOutputStream out=keep?new ByteArrayOutputStream():null;
    int n,size=0;
    while((n=z.read(b))!=-1){
     size+=n;total+=n;
     if(size>8*1024*1024||total>LIMIT)throw new IOException("Превышен лимит распаковки XLSX");
     if(keep)out.write(b,0,n);
    }
    if(keep)found.put(name,out.toByteArray());
    z.closeEntry();
   }
  }
  return found;
 }
 private static Document xml(byte[] data) throws Exception {
  DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();
  f.setNamespaceAware(true);f.setXIncludeAware(false);f.setExpandEntityReferences(false);
  f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
  f.setFeature("http://xml.org/sax/features/external-general-entities",false);
  f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
  return f.newDocumentBuilder().parse(new ByteArrayInputStream(data));
 }
 private static String clean(String s){return (s==null?"":s).replace("_x000D_"," ").replace('\r',' ').replace('\n',' ').replaceAll("\\s+"," ").trim();}
 private static String str(String[] row,int i){return row==null||i<0||i>=row.length||row[i]==null?"":row[i];}
 private static int number(String x,int fallback){try{return Integer.parseInt(x);}catch(Exception e){return fallback;}}
 static ArrayList<String> sharedStrings(byte[] data) throws Exception {
  ArrayList<String> values=new ArrayList<>();if(data==null)return values;
  NodeList si=xml(data).getElementsByTagNameNS("*","si");
  for(int i=0;i<si.getLength();i++){
   NodeList ts=((Element)si.item(i)).getElementsByTagNameNS("*","t");
   StringBuilder b=new StringBuilder();
   for(int j=0;j<ts.getLength();j++)b.append(ts.item(j).getTextContent());
   values.add(clean(b.toString()));
  }
  return values;
 }
 private static int column(String x){
  int col=0;
  for(int j=0;j<x.length()&&Character.isLetter(x.charAt(j));j++)col=col*26+Character.toUpperCase(x.charAt(j))-'A'+1;
  return col-1;
 }
 static ArrayList<String[]> rows(byte[] data,ArrayList<String> shared) throws Exception {
  ArrayList<String[]> all=new ArrayList<>();
  if(data==null)return all;
  NodeList rows=xml(data).getElementsByTagNameNS("*","row");
  for(int i=0;i<rows.getLength()&&i<15000;i++){
   Element row=(Element)rows.item(i);
   int line=number(row.getAttribute("r"),all.size()+1);
   if(line<1||line>15000)continue;
   String[] values=new String[18];
   NodeList children=row.getChildNodes();
   for(int k=0;k<children.getLength();k++){
    if(!(children.item(k) instanceof Element)||!"c".equals(children.item(k).getLocalName()))continue;
    Element c=(Element)children.item(k);
    int col=column(c.getAttribute("r"));
    if(col<0||col>=values.length)continue;
    String type=c.getAttribute("t"),v="";
    NodeList items=c.getElementsByTagNameNS("*","v");
    if(items.getLength()>0)v=items.item(0).getTextContent();
    if("s".equals(type)){int id=number(v,-1);v=id>=0&&id<shared.size()?shared.get(id):"";}
    else if("inlineStr".equals(type)){
     StringBuilder b=new StringBuilder();NodeList ts=c.getElementsByTagNameNS("*","t");
     for(int j=0;j<ts.getLength();j++)b.append(ts.item(j).getTextContent());
     v=b.toString();
    }
    values[col]=clean(v);
   }
   while(all.size()<line-1)all.add(new String[18]);
   if(all.size()==line-1)all.add(values);else all.set(line-1,values);
  }
  return all;
 }
 private static boolean has(ArrayList<String[]> rows,String search){
  for(int i=0;i<Math.min(rows.size(),60);i++)for(String s:rows.get(i))
   if(s!=null&&s.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)))return true;
  return false;
 }
 static Integer count(String s){
  try{if(s.isEmpty())return null;double n=Double.parseDouble(s.replace(',','.'));return n>=0&&n<=10000000&&n==Math.floor(n)?(int)n:null;}
  catch(Exception ex){return null;}
 }
 static String service(String s){
  s=s.toLowerCase(Locale.ROOT);
  if(s.contains("горячее водоснабжение"))return "Горячее водоснабжение (ГВС)";
  if(s.contains("холодное водоснабжение"))return "Холодное водоснабжение (ХВС)";
  if(s.contains("теплоснабжение"))return "Теплоснабжение";
  if(s.contains("электроснабжение"))return "Электроснабжение";
  if(s.contains("газоснабжение"))return "Газоснабжение";
  if(s.contains("водоотведение")||s.contains("канализация"))return "Водоотведение";
  return "";
 }
 static String date(String d,String t){
  if(d.isEmpty()&&t.isEmpty())return "";
  try{
   double v=Double.parseDouble(d.replace(',','.'));
   if(v>=20000&&v<80000){
    Calendar c=Calendar.getInstance(TimeZone.getTimeZone("UTC"));c.clear();c.set(1899,11,30);c.add(Calendar.DAY_OF_YEAR,(int)v);
    d=String.format(Locale.ROOT,"%02d.%02d.%04d",c.get(Calendar.DAY_OF_MONTH),c.get(Calendar.MONTH)+1,c.get(Calendar.YEAR));
   }
  }catch(Exception ignored){}
  try{
   double v=Double.parseDouble(t.replace(',','.'));
   if(v>=0&&v<1){int minutes=(int)Math.round(v*1440);t=String.format(Locale.ROOT,"%02d:%02d",(minutes/60)%24,minutes%60);}
  }catch(Exception ignored){}
  return (d+" "+t).trim();
 }
 static String hours(String h){
  try{double n=Double.parseDouble(h.replace(',','.'));return n>=0&&n<10000?(n==Math.floor(n)?(long)n+" ч":String.format(new Locale("ru"),"%.1f ч",n)):h;}
  catch(Exception ex){return h;}
 }
 private static void operational(ArrayList<String[]> rows,String file,Parsed parsed){
  boolean samara=has(rows,"г. Самары")||has(rows,"г.Самары")||has(rows,"города Самары");
  String current="";
  for(String[] row:rows){
   String section=service(str(row,0));
   if(!section.isEmpty()&&str(row,0).length()<100){current=section;continue;}
   if(!str(row,0).matches("[0-9]{1,4}")||str(row,2).isEmpty()||current.isEmpty())continue;
   Operational o=new Operational();o.source=file;o.service=current;
   o.district=str(row,1);o.municipality=samara?"Самара":str(row,1);
   o.address=str(row,2);o.houses=count(str(row,3));o.people=count(str(row,4));
   o.company=str(row,6);o.reason=str(row,7);
   o.start=date(str(row,9),str(row,10));o.end=date(str(row,11),str(row,12));
   o.duration=hours(str(row,13));
   o.status=o.end.isEmpty()?"Окончание не указано; статус работ требует проверки":"Время окончания указано, восстановление не подтверждено";
   String reason=o.reason.toLowerCase(Locale.ROOT);
   if(reason.contains("ведутся")||reason.contains("продолжаются")||reason.contains("в работе"))o.status="Работы ведутся (по тексту источника)";
   parsed.operations.add(o);
  }
 }
 private static void incidents(ArrayList<String[]> rows,String file,Parsed parsed){
  for(String[] row:rows){
   if(!str(row,0).matches("[0-9]{1,5}")||!str(row,1).matches(".*[0-9]{2}\\.[0-9]{2}\\.[0-9]{4}.*"))continue;
   Incident o=new Incident();o.source=file;o.timestamp=(str(row,1)+" "+str(row,2)).trim();
   o.municipality=str(row,3).isEmpty()?"Не указан":str(row,3);
   o.address=str(row,4);o.unit=str(row,5);o.characteristic=str(row,6);o.reason=str(row,7);o.measures=str(row,8);
   if(!o.address.isEmpty()||!o.reason.isEmpty())parsed.incidents.add(o);
  }
 }
 static boolean isSamara(String s){
  s=clean(s).toLowerCase(Locale.ROOT);
  return s.equals("самара")||s.equals("г. самара")||s.equals("г самара")||s.equals("город самара")||s.startsWith("самара, ");
 }
}
