package ru.samara.mailreport;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
public class MainActivity extends Activity {
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final Calendar selected=Calendar.getInstance();
    private ArrayList<AccountStore.Account> accounts=new ArrayList<>();
    private AccountStore store;
    private TextView accountList,report,status;
    private Button run,share,dayButton;
    private String lastReport="";
    private final SimpleDateFormat formatter=new SimpleDateFormat("dd.MM.yyyy",new Locale("ru"));
    @Override public void onCreate(Bundle b){super.onCreate(b);store=new AccountStore(this);
        try{accounts=store.read();}catch(Exception e){Toast.makeText(this,"Не удалось расшифровать сохраненные ящики",Toast.LENGTH_LONG).show();}
        ScrollView scroll=new ScrollView(this);LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(28,32,28,32);scroll.addView(layout);
        TextView header=new TextView(this);header.setText("Самара — аварии и отключения");header.setTextSize(22);layout.addView(header);
        TextView hint=new TextView(this);hint.setText("Сбор писем из нескольких IMAP-ящиков и предварительная оперативная сводка за выбранную дату. Данные обрабатываются на телефоне.");hint.setPadding(0,14,0,18);layout.addView(hint);
        accountList=new TextView(this);layout.addView(accountList);
        Button add=new Button(this);add.setText("Добавить почтовый ящик");layout.addView(add);add.setOnClickListener(v->showAccountDialog());
        Button remove=new Button(this);remove.setText("Удалить ящик");layout.addView(remove);remove.setOnClickListener(v->removeDialog());
        dayButton=new Button(this);layout.addView(dayButton);updateDate();dayButton.setOnClickListener(v->new DatePickerDialog(this,(picker,y,m,d)->{
            selected.set(y,m,d);updateDate();},selected.get(Calendar.YEAR),selected.get(Calendar.MONTH),selected.get(Calendar.DATE)).show());
        run=new Button(this);run.setText("Сформировать отчет");layout.addView(run);run.setOnClickListener(v->loadMail());
        status=new TextView(this);status.setPadding(0,12,0,12);layout.addView(status);
        share=new Button(this);share.setText("Поделиться отчетом");share.setEnabled(false);layout.addView(share);share.setOnClickListener(v->shareReport());
        report=new TextView(this);report.setTextIsSelectable(true);report.setTextSize(15);layout.addView(report);
        setContentView(scroll);refreshAccounts();
    }
    private void updateDate(){dayButton.setText("Дата: "+formatter.format(selected.getTime()));}
    private void refreshAccounts(){StringBuilder s=new StringBuilder("Почтовые ящики ("+accounts.size()+"):\n");
        for(AccountStore.Account a:accounts)s.append("• ").append(a.title).append(" — ").append(a.login).append("\n");accountList.setText(s);}
    private EditText field(String hint,String preset,LinearLayout parent){EditText e=new EditText(this);e.setSingleLine(true);e.setHint(hint);e.setText(preset);parent.addView(e);return e;}
    private void showAccountDialog(){LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(28,8,28,8);
        EditText label=field("Название (например, Т Плюс)","",body);
        EditText host=field("IMAP сервер (например, imap.mail.ru)","",body);
        EditText port=field("SSL порт","993",body);port.setInputType(2);
        EditText login=field("Почтовый адрес / логин","",body);
        EditText password=field("Пароль приложения для IMAP","",body);password.setInputType(129);
        new AlertDialog.Builder(this).setTitle("Новый IMAP-ящик").setView(body).setNegativeButton("Отмена",null)
            .setPositiveButton("Сохранить",(dialog,which)->{
                try{String h=host.getText().toString().trim(),l=login.getText().toString().trim(),p=password.getText().toString();
                    if(h.isEmpty()||l.isEmpty()||p.isEmpty())throw new IllegalArgumentException("Заполните сервер, логин и пароль");
                    accounts.add(new AccountStore.Account(label.getText().toString().trim().isEmpty()?l:label.getText().toString().trim(),h,Integer.parseInt(port.getText().toString()),l,p));
                    store.save(accounts);refreshAccounts();
                }catch(Exception e){Toast.makeText(this,"Ошибка: "+e.getMessage(),Toast.LENGTH_LONG).show();}
            }).show();
    }
    private void removeDialog(){if(accounts.isEmpty())return;String[] choices=new String[accounts.size()];
        for(int i=0;i<accounts.size();i++)choices[i]=accounts.get(i).title+" ("+accounts.get(i).login+")";
        new AlertDialog.Builder(this).setTitle("Удалить подключение").setItems(choices,(d,index)->{
            AccountStore.Account removed=accounts.remove(index);try{store.save(accounts);refreshAccounts();}catch(Exception e){accounts.add(index,removed);Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}
        }).show();
    }
    private void loadMail(){if(accounts.isEmpty()){status.setText("Сначала добавьте хотя бы один почтовый ящик.");return;}
        run.setEnabled(false);share.setEnabled(false);status.setText("Подключаемся к почте…");report.setText("");
        Date date=selected.getTime();ArrayList<AccountStore.Account> snapshots=new ArrayList<>(accounts);
        executor.execute(()->{
            ArrayList<ReportEngine.Item> all=new ArrayList<>();ArrayList<String> errors=new ArrayList<>();
            for(AccountStore.Account a:snapshots){runOnUiThread(()->status.setText("Проверка: "+a.title));
                try{all.addAll(MailReader.fetch(a,date));}catch(Exception e){errors.add(a.title+": "+e.getClass().getSimpleName()+" — "+e.getMessage());}
            }
            String result=ReportEngine.build(date,all,errors);
            runOnUiThread(()->{lastReport=result;report.setText(result);status.setText("Готово. Получено писем: "+all.size()+"; ошибки ящиков: "+errors.size());run.setEnabled(true);share.setEnabled(true);});
        });
    }
    private void shareReport(){Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_SUBJECT,"Оперативная сводка по Самаре — "+formatter.format(selected.getTime()));
        i.putExtra(Intent.EXTRA_TEXT,lastReport);startActivity(Intent.createChooser(i,"Отправить отчет"));}
    @Override protected void onDestroy(){executor.shutdownNow();super.onDestroy();}
}
