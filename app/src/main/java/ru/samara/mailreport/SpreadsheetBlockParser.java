package ru.samara.mailreport;

import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the two known report layouts where labels and values are arranged as
 * multi-row blocks instead of a conventional table header.
 */
final class SpreadsheetBlockParser {
    private static final Pattern DATE = Pattern.compile("(?<!\\d)(\\d{1,2}\\.\\d{1,2}(?:\\.\\d{2,4})?)(?!\\d)");
    private static final Pattern TIME = Pattern.compile("(?<!\\d)(\\d{1,2}:\\d{2})(?!\\d)");
    private static final Pattern NUMBER = Pattern.compile("^\\s*(\\d{1,7})(?:[.,]0+)?\\s*$");
    private static final String[] SAMARA_DISTRICTS = {
        "кировский","железнодорожный","промышленный","ленинский","советский",
        "самарский","октябрьский","красноглинский","куйбышевский"
    };

    private SpreadsheetBlockParser() {}

    static int parseIncidents(ArrayList<String[]> rows, String file,
                              SpreadsheetAttachmentReader.Parsed out) {
        int before = out.incidents.size();
        SpreadsheetAttachmentReader.Incident current = null;
        for (String[] row : rows) {
            if (row == null) continue;
            String text = join(row);
            if (hasLabel(row, "вид инцидента")) {
                if (current != null) finishIncident(current, out);
                current = new SpreadsheetAttachmentReader.Incident();
                current.source = file;
                String kindText = valueAfter(row, "вид инцидента");
                setKindAndDescription(current, kindText);
                continue;
            }
            if (current == null) continue;

            String value = valueAfter(row, "адрес");
            if (!value.isEmpty()) {
                AddressParts parts = splitAddress(value);
                current.address = parts.address;
                current.municipality = parts.municipality;
                String unit = trailingUnit(row);
                if (!unit.isEmpty()) current.unit = unit;
            }

            value = valueAfter(row, "характеристики объекта", "характеристика объекта");
            if (!value.isEmpty()) current.characteristic = value;

            value = valueAfter(row, "обнаружено");
            if (!value.isEmpty()) current.description = value;

            value = valueAfter(row, "принятые меры", "принятые мероприятия");
            if (!value.isEmpty()) current.measures = value;

            if (current.timestamp == null || current.timestamp.isEmpty()) {
                Matcher d = DATE.matcher(text);
                if (d.find()) {
                    String date = d.group(1);
                    Matcher tm = TIME.matcher(text.substring(d.end()));
                    String time = tm.find() ? tm.group(1) : "";
                    current.timestamp = (date + (time.isEmpty() ? "" : " в " + time)).trim();
                }
            }
            if (current.unit == null || current.unit.isEmpty()) {
                String unit = trailingUnit(row);
                if (!unit.isEmpty()) current.unit = unit;
            }
        }
        if (current != null) finishIncident(current, out);
        return out.incidents.size() - before;
    }

    private static void finishIncident(SpreadsheetAttachmentReader.Incident item,
                                       SpreadsheetAttachmentReader.Parsed out) {
        if (empty(item.municipality)) item.municipality = "Не указан";
        if (empty(item.address) && !empty(item.description)) {
            // Do not fabricate an address; retain the record so the missing field is visible.
            item.address = "Адрес не указан";
        }
        if (empty(item.kind)) item.kind = "Не определён";
        if (empty(item.description)) item.description = "Не указано";
        if (empty(item.characteristic)) item.characteristic = "Не указаны";
        if (empty(item.measures)) item.measures = "Не указаны";
        if (!empty(item.address) && (!"Не указано".equals(item.description)
                || !"Не определён".equals(item.kind))) {
            out.incidents.add(item);
        }
    }

    private static void setKindAndDescription(SpreadsheetAttachmentReader.Incident item,
                                               String text) {
        String value = clean(text);
        if (value.isEmpty()) return;
        String lower = norm(value);
        String[] known = {
            "нарушение тб в гх", "нарушение тб", "возгорание", "пожар",
            "утечка газа", "утечка", "отравление", "взрыв", "задымление"
        };
        for (String kind : known) {
            if (lower.equals(kind) || lower.startsWith(kind + ".") || lower.startsWith(kind + ":")) {
                item.kind = canonicalKind(kind);
                item.description = value.substring(Math.min(value.length(), kind.length()))
                    .replaceFirst("^[\\s.:;—-]+", "").trim();
                return;
            }
        }
        int period = value.indexOf('.');
        if (period > 0 && period < 70) {
            item.kind = value.substring(0, period).trim();
            item.description = value.substring(period + 1).trim();
        } else {
            item.kind = value;
        }
    }

    private static String canonicalKind(String kind) {
        if (kind.equals("нарушение тб в гх") || kind.equals("нарушение тб")) return "Нарушение ТБ в ГХ";
        if (kind.equals("возгорание")) return "Возгорание";
        if (kind.equals("пожар")) return "Пожар";
        if (kind.equals("утечка газа") || kind.equals("утечка")) return "Утечка газа";
        if (kind.equals("отравление")) return "Отравление";
        if (kind.equals("взрыв")) return "Взрыв";
        if (kind.equals("задымление")) return "Задымление";
        return kind;
    }

    static int parseOperational(ArrayList<String[]> rows, String file,
                                SpreadsheetAttachmentReader.Parsed out) {
        int before = out.operations.size();
        boolean samaraContext = contains(rows, "г. самара") || contains(rows, "город самара")
            || contains(rows, "по самаре") || contains(rows, "г. самары");
        String currentService = "";
        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            if (row == null) continue;
            String text = join(row);
            String foundService = serviceIn(text);
            boolean start = operationalRecordStart(row);
            if (!foundService.isEmpty() && !start) {
                currentService = foundService;
                continue;
            }
            if (!start) continue;

            int end = i + 1;
            while (end < rows.size()) {
                String[] next = rows.get(end);
                if (next != null && operationalRecordStart(next)) break;
                String section = next == null ? "" : serviceIn(join(next));
                if (!section.isEmpty() && next != null && !operationalRecordStart(next)) break;
                end++;
            }
            ArrayList<String[]> block = new ArrayList<>();
            for (int k = i; k < end; k++) if (rows.get(k) != null) block.add(rows.get(k));
            SpreadsheetAttachmentReader.Operational item = parseOneOperational(block, file,
                currentService, samaraContext);
            if (item != null) out.operations.add(item);
            i = end - 1;
        }
        return out.operations.size() - before;
    }

    private static SpreadsheetAttachmentReader.Operational parseOneOperational(
        ArrayList<String[]> block, String file, String service, boolean samaraContext) {
        if (block.isEmpty()) return null;
        SpreadsheetAttachmentReader.Operational item = new SpreadsheetAttachmentReader.Operational();
        item.source = file;
        item.service = service;
        String all = "";
        for (String[] row : block) all += " " + join(row);

        String[] first = block.get(0);
        item.district = valueAfter(first, "район");
        String objects = valueAfter(first, "объекты", "объект");
        if (empty(objects)) {
            for (String[] row : block) {
                objects = valueAfter(row, "объекты", "объект");
                if (!empty(objects)) break;
            }
        }
        item.address = objects;

        int objectCell = labelCell(first, "объекты", "объект");
        if (objectCell >= 0) {
            ArrayList<Integer> nums = new ArrayList<>();
            for (int j = objectCell + 1; j < first.length; j++) {
                String cell = clean(first[j]);
                Matcher m = NUMBER.matcher(cell);
                if (m.matches()) nums.add(Integer.parseInt(m.group(1)));
            }
            if (!nums.isEmpty()) item.houses = nums.get(0);
            if (nums.size() > 1) item.people = nums.get(1);
        }
        if (item.houses == null || item.people == null) {
            ArrayList<Integer> nums = new ArrayList<>();
            for (String[] row : block) {
                for (String cell : row) {
                    Matcher m = NUMBER.matcher(clean(cell));
                    if (m.matches()) nums.add(Integer.parseInt(m.group(1)));
                }
            }
            if (item.houses == null && nums.size() >= 2) item.houses = nums.get(1);
            if (item.people == null && nums.size() >= 3) item.people = nums.get(2);
        }

        for (String[] row : block) {
            String text = join(row);
            String value = valueAfter(row, "причины", "причина");
            if (!empty(value)) {
                String next = nextValueAfterLabel(row, "причины", "причина");
                if (looksLikeCompany(value)) {
                    item.company = value;
                    if (!empty(next) && !looksLikeLabel(next)) item.reason = next;
                } else {
                    item.reason = value;
                    if (empty(item.company)) item.company = companyIn(value);
                }
            }
            value = valueAfter(row, "ответственный");
            if (!empty(value)) item.responsible = value;
            value = valueAfter(row, "перерыв");
            if (!empty(value)) item.duration = duration(value);
            if (hasLabel(row, "длительность")) {
                String[] times = extractInterval(row);
                if (times[0] != null) item.start = times[0];
                if (times[1] != null) item.end = times[1];
            }
            if (item.service == null || item.service.isEmpty()) {
                String s = serviceIn(text);
                if (!s.isEmpty()) item.service = s;
            }
            String lower = norm(text);
            if (lower.contains("работы ведутся") || lower.contains("ведутся работы")
                    || lower.contains("продолжаются работы") || lower.contains("в работе")) {
                item.status = "Работы ведутся";
            } else if (lower.contains("восстановлен") || lower.contains("ресурс запущен")
                    || lower.contains("работы завершены")) {
                item.status = "Восстановлено (по тексту источника)";
            }
        }

        if (empty(item.municipality)) {
            if (isSamaraDistrict(item.district) || samaraContext) item.municipality = "Самара";
            else item.municipality = "Не указан";
        }
        if (empty(item.service)) item.service = "Услуга не указана";
        if (empty(item.address) && empty(item.reason)) return null;
        if (empty(item.reason)) item.reason = "Не указана";
        if (empty(item.company)) item.company = "Не указано";
        if (empty(item.responsible)) item.responsible = "Не указан";
        if (empty(item.duration)) item.duration = "Не указана";
        if (empty(item.start)) item.start = "Не указано";
        if (empty(item.end)) item.end = "Не указано";
        if (empty(item.status)) item.status = "Статус не указан";
        return item;
    }

    private static String[] extractInterval(String[] row) {
        String text = join(row);
        int idx = text.toLowerCase(Locale.ROOT).indexOf("длительность");
        String part = idx >= 0 ? text.substring(idx + "длительность".length()) : text;
        Matcher dates = DATE.matcher(part);
        ArrayList<String> d = new ArrayList<>();
        while (dates.find() && d.size() < 2) d.add(dates.group(1));
        Matcher times = TIME.matcher(part);
        ArrayList<String> t = new ArrayList<>();
        while (times.find() && t.size() < 2) t.add(t.group(1));
        String start = null, end = null;
        if (!d.isEmpty() && !t.isEmpty()) start = d.get(0) + " " + t.get(0);
        if (d.size() > 1 && t.size() > 1) end = d.get(1) + " " + t.get(1);
        return new String[]{start, end};
    }

    private static String duration(String value) {
        String v = clean(value);
        Matcher m = Pattern.compile("(?iu)(\\d+(?:[.,]\\d+)?)\\s*(?:час(?:а|ов)?|ч\\.?)(?:\\b|$)").matcher(v);
        if (m.find()) return m.group(1).replace(',', '.') + " ч";
        return v;
    }

    private static boolean operationalRecordStart(String[] row) {
        for (String cell : row) {
            String n = norm(cell);
            if (n.matches("^(?:район|объекты|объект)\\s*:.*")) return true;
        }
        return false;
    }

    private static String serviceIn(String text) {
        String t = norm(text);
        if (t.contains("теплоснабжен") || t.contains("отоплен") || t.matches(".*\\bтсн\\b.*")
                || t.matches(".*\\bт/с\\b.*")) return "Теплоснабжение";
        if ((t.contains("горяч") && t.contains("вод")) || t.matches(".*\\bгвс\\b.*")) return "Горячее водоснабжение (ГВС)";
        if ((t.contains("холодн") && t.contains("вод")) || t.matches(".*\\bхвс\\b.*")) return "Холодное водоснабжение (ХВС)";
        if (t.contains("электроснабжен") || t.contains("электроэнерг") || t.matches(".*\\bэсн\\b.*")) return "Электроснабжение";
        if (t.contains("газоснабжен") || t.matches(".*\\bгаз\\b.*")) return "Газоснабжение";
        if (t.contains("водоотведен") || t.contains("канализац")) return "Водоотведение";
        return "";
    }

    private static boolean isSamaraDistrict(String district) {
        String d = norm(district);
        for (String s : SAMARA_DISTRICTS) if (d.equals(s)) return true;
        return false;
    }

    private static AddressParts splitAddress(String value) {
        String v = clean(value);
        AddressParts p = new AddressParts();
        p.municipality = "Не указан";
        p.address = v;
        if (v.isEmpty()) return p;

        Matcher samara = Pattern.compile("(?iu)^\\s*(?:г\\.?\\s*)?самара\\s*(?:,\\s*|\\s+)?(.*)$").matcher(v);
        if (samara.matches()) {
            p.municipality = "Самара";
            p.address = samara.group(1).trim().replaceFirst("^,\\s*", "");
            return p;
        }
        int comma = v.indexOf(',');
        if (comma > 0 && comma < 50) {
            String city = v.substring(0, comma).trim().replaceFirst("(?iu)^г\\.?\\s*", "");
            String rest = v.substring(comma + 1).trim();
            if (!city.isEmpty() && rest.matches("(?iu)^(?:ул\\.?|улица|проспект|пр\\.?|переулок|пер\\.?|шоссе|наб\\.?|набережная|площадь|дом|д\\.?|[а-яё].*\\d.*).*$")) {
                p.municipality = city;
                p.address = rest;
            }
        }
        return p;
    }

    private static String trailingUnit(String[] row) {
        for (String cell : row) {
            String v = clean(cell);
            if (norm(v).matches("^(?:отд\\.?|отделение|участок|служба)\\b.*")) return v;
        }
        return "";
    }

    private static String valueAfter(String[] row, String... labels) {
        int index = labelCell(row, labels);
        if (index < 0) return "";
        String cell = clean(row[index]);
        String n = norm(cell);
        for (String label : labels) {
            int at = n.indexOf(norm(label));
            if (at >= 0) {
                String value = cell.substring(Math.min(cell.length(), at + label.length()))
                    .replaceFirst("^[\\s:：=—-]+", "").trim();
                if (!value.isEmpty()) return value;
            }
        }
        for (int i = index + 1; i < row.length; i++) {
            String value = clean(row[i]);
            if (value.isEmpty() || looksLikeLabel(value)) continue;
            return value;
        }
        return "";
    }

    private static String nextValueAfterLabel(String[] row, String... labels) {
        int index = labelCell(row, labels);
        if (index < 0) return "";
        boolean after = false;
        for (int i = index; i < row.length; i++) {
            String value = clean(row[i]);
            if (!after) {
                after = true;
                String n = norm(value);
                for (String label : labels) {
                    int at = n.indexOf(norm(label));
                    if (at >= 0) {
                        String tail = value.substring(Math.min(value.length(), at + label.length()))
                            .replaceFirst("^[\\s:：=—-]+", "").trim();
                        if (!tail.isEmpty()) return tail;
                    }
                }
                continue;
            }
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static int labelCell(String[] row, String... labels) {
        for (int i = 0; i < row.length; i++) {
            String n = norm(row[i]);
            for (String label : labels) {
                if (n.contains(norm(label))) return i;
            }
        }
        return -1;
    }

    private static boolean hasLabel(String[] row, String... labels) {
        return labelCell(row, labels) >= 0;
    }

    private static boolean looksLikeLabel(String value) {
        String n = norm(value);
        return n.matches("^(?:вид инцидента|адрес|характеристики объекта|характеристика объекта|обнаружено|принятые меры|район|объекты|объект|причины|причина|ответственный|длительность|перерыв)\\s*[:：]?\\s*$");
    }

    private static boolean looksLikeCompany(String value) {
        String n = norm(value);
        return n.matches("^(?:т\\s*плюс|пao\\s*т\\s*плюс|скс|самарские коммунальные системы|самэк|самараэнерго)\\b.*");
    }

    private static String companyIn(String value) {
        String n = norm(value);
        if (n.contains("т плюс") || n.contains("тплюс")) return "Т Плюс";
        if (n.contains("скс") || n.contains("самарские коммунальные системы")) return "СКС";
        if (n.contains("самэк")) return "САМЭК";
        return "";
    }

    private static boolean contains(ArrayList<String[]> rows, String needle) {
        String n = norm(needle);
        for (String[] row : rows) if (norm(join(row)).contains(n)) return true;
        return false;
    }

    private static String join(String[] row) {
        StringBuilder b = new StringBuilder();
        for (String cell : row) if (cell != null && !cell.trim().isEmpty()) {
            if (b.length() > 0) b.append(' ');
            b.append(cell.trim());
        }
        return b.toString();
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\u00a0',' ').replaceAll("\\s+"," ").trim();
    }

    private static String norm(String value) {
        return clean(value).toLowerCase(Locale.ROOT).replace('ё','е');
    }

    private static boolean empty(String s) { return s == null || s.trim().isEmpty(); }

    private static final class AddressParts {
        String municipality;
        String address;
    }
}
