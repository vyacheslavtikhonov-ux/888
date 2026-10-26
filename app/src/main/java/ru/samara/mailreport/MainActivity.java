package ru.samara.mailreport;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.content.SharedPreferences;
import android.widget.Button;
import android.widget.CheckBox;
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
    private static final int SLOT_COUNT=2;
    private static final String MAIL_HOST="imap.mail.ru";
    private static final int MAIL_PORT=993;
    private static final String WEBMAIL_URL="https://e.mail.ru/";
    private static final String FIRST_ADDRESS="infoedds2@mail.ru";
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final Calendar selected=Calendar.getInstance();
    private final ArrayList<AccountStore.Account> accounts=new ArrayList<>();
    private final ArrayList<CheckBox> enabledSlots=new ArrayList<>();
    private final ArrayList<EditText> emailFields=new ArrayList<>();
    private final ArrayList<EditText> passwordFields=new ArrayList<>();
    private AccountStore store;
    private TextView report,status;
    private Button run,share,dayButton,test;
    private String lastReport="";
    private final SimpleDateFormat formatter=new SimpleDateFormat("dd.MM.yyyy",new Locale("ru"));

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        store=new AccountStore(this);
        try{accounts.addAll(store.read());}
        catch(Exception e){Toast.makeText(this,"Не удалось прочитать сохранённые ящики",Toast.LENGTH_LONG).show();}
        while(accounts.size()>SLOT_COUNT)accounts.remove(accounts.size()-1);
        while(accounts.size()<SLOT_COUNT){
            int n=accounts.size();
            accounts.add(new AccountStore.Account("Ящик "+(n+1),MAIL_HOST,MAIL_PORT,n==0?FIRST_ADDRESS:"",""));
        }

        ScrollView scroll=new ScrollView(this);
        LinearLayout layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(28,32,28,32);
        scroll.addView(layout);

        TextView header=new TextView(this);
        header.setText("Самара — почтовая сводка");
        header.setTextSize(22);
        layout.addView(header);
        TextView hint=new TextView(this);
        hint.setText("Два ящика Mail.ru. Кнопка «Проверка работы почты» открывает официальный сайт в установленном браузере для ручного входа, включая 2FA и капчу. Вход сохраняется самим браузером, но браузерная сессия не предоставляет приложению доступ к письмам. Для автоматического отчёта нужен отдельный пароль IMAP (imap.mail.ru:993, SSL/TLS).");
        hint.setPadding(0,14,0,18);
        layout.addView(hint);

        SharedPreferences prefs=getSharedPreferences("slots",MODE_PRIVATE);
        for(int i=0;i<SLOT_COUNT;i++)addSlot(layout,i,prefs);

        Button save=new Button(this);
        save.setText("Сохранить 2 ящика");
        layout.addView(save);
        save.setOnClickListener(v->{
            try{saveConfiguration();status.setText("Настройки сохранены на телефоне.");}
            catch(Exception e){status.setText("Ошибка сохранения: "+e.getMessage());}
        });
        dayButton=new Button(this);
        layout.addView(dayButton);
        updateDate();
        dayButton.setOnClickListener(v->new DatePickerDialog(this,(picker,y,m,d)->{
            selected.set(y,m,d);updateDate();
        },selected.get(Calendar.YEAR),selected.get(Calendar.MONTH),selected.get(Calendar.DATE)).show());

        test=new Button(this);
        test.setText("Проверить IMAP выбранных ящиков");
        layout.addView(test);
        test.setOnClickListener(v->testConnections());

        run=new Button(this);
        run.setText("Сформировать отчёт за дату");
        layout.addView(run);
        run.setOnClickListener(v->loadMail());

        status=new TextView(this);
        status.setPadding(0,12,0,12);
        layout.addView(status);
        share=new Button(this);
        share.setText("Поделиться текстовым отчётом");
        share.setEnabled(false);
        layout.addView(share);
        share.setOnClickListener(v->shareReport());
        report=new TextView(this);
        report.setTextIsSelectable(true);
        report.setTextSize(15);
        layout.addView(report);
        setContentView(scroll);
    }

    private void addSlot(LinearLayout parent,int index,SharedPreferences prefs){
        AccountStore.Account a=accounts.get(index);
        TextView title=new TextView(this);
        title.setText("\nЯщик "+(index+1));
        title.setTextSize(18);
        parent.addView(title);

        CheckBox enabled=new CheckBox(this);
        enabled.setText("Включить в отчёт");
        enabled.setChecked(prefs.getBoolean("mail_selected_"+index,index==0));
        parent.addView(enabled);
        enabledSlots.add(enabled);

        EditText email=new EditText(this);
        email.setHint("Адрес почты Mail.ru");
        email.setSingleLine(true);
        email.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        email.setText(a.login);
        parent.addView(email);
        emailFields.add(email);

        EditText password=new EditText(this);
        password.setHint(a.secret.isEmpty()?"Пароль для IMAP (НЕ пароль сайта)":"Пароль IMAP сохранён • оставьте пустым, чтобы не менять");
        password.setSingleLine(true);
        password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        parent.addView(password);
        passwordFields.add(password);

        Button browser=new Button(this);
        browser.setText("Проверка работы почты №"+(index+1)+" — открыть Mail.ru");
        parent.addView(browser);
        browser.setOnClickListener(v->{
            try{
                saveConfiguration();
                Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse(WEBMAIL_URL));
                startActivity(intent);
            }catch(Exception e){
                Toast.makeText(this,"Не удалось открыть почту: "+e.getMessage(),Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateDate(){dayButton.setText("Дата писем: "+formatter.format(selected.getTime()));}

    private void saveConfiguration() throws Exception {
        SharedPreferences.Editor prefs=getSharedPreferences("slots",MODE_PRIVATE).edit();
        ArrayList<AccountStore.Account> updated=new ArrayList<>();
        for(int i=0;i<SLOT_COUNT;i++){
            String email=emailFields.get(i).getText().toString().trim();
            String enteredPassword=passwordFields.get(i).getText().toString();
            AccountStore.Account previous=accounts.get(i);
            String secret=(!enteredPassword.isEmpty())?enteredPassword:
                (email.equalsIgnoreCase(previous.login)?previous.secret:"");
            if(!email.isEmpty()&&(!email.contains("@")||email.contains(" "))){
                throw new IllegalArgumentException("Неверный адрес в слоте "+(i+1));
            }
            updated.add(new AccountStore.Account("Ящик "+(i+1)+" ("+email+")",MAIL_HOST,MAIL_PORT,email,secret));
            prefs.putBoolean("mail_selected_"+i,enabledSlots.get(i).isChecked());
        }
        store.save(updated);
        if(!prefs.commit())throw new IllegalStateException("Не удалось записать выбор ящиков");
        accounts.clear();
        accounts.addAll(updated);
        for(int i=0;i<SLOT_COUNT;i++){
            passwordFields.get(i).setText("");
            passwordFields.get(i).setHint(accounts.get(i).secret.isEmpty()?"Пароль для IMAP (НЕ пароль сайта)":"Пароль IMAP сохранён • оставьте пустым, чтобы не менять");
        }
    }

    private ArrayList<AccountStore.Account> selectedAccounts(ArrayList<String> errors){
        ArrayList<AccountStore.Account> result=new ArrayList<>();
        for(int i=0;i<SLOT_COUNT;i++){
            if(!enabledSlots.get(i).isChecked())continue;
            AccountStore.Account a=accounts.get(i);
            if(a.login.isEmpty()||a.secret.isEmpty()){
                errors.add("Ящик "+(i+1)+": не указан адрес или пароль внешнего приложения");
            } else result.add(a);
        }
        return result;
    }

    private void testConnections(){
        try{saveConfiguration();}
        catch(Exception e){status.setText("Проверьте настройки: "+e.getMessage());return;}
        ArrayList<String> errors=new ArrayList<>();
        ArrayList<AccountStore.Account> choices=selectedAccounts(errors);
        if(choices.isEmpty()){status.setText("Нет готовых к проверке ящиков. "+String.join("; ",errors));return;}
        test.setEnabled(false);run.setEnabled(false);
        status.setText("Проверяем доступ к Mail.ru...");
        executor.execute(()->{
            ArrayList<String> result=new ArrayList<>();
            for(AccountStore.Account a:choices){
                try{MailReader.fetch(a,selected.getTime());result.add("✓ "+a.login+": подключение работает");}
                catch(javax.mail.AuthenticationFailedException e){result.add("✗ "+a.login+": отказ в авторизации. Используйте пароль для внешнего приложения Mail.ru.");}
                catch(Exception e){result.add("✗ "+a.login+": ошибка соединения ("+e.getClass().getSimpleName()+")");}
            }
            result.addAll(errors);
            runOnUiThread(()->{
                status.setText(String.join("\n",result));
                test.setEnabled(true);run.setEnabled(true);
            });
        });
    }

    private void loadMail(){
        try{saveConfiguration();}
        catch(Exception e){status.setText("Проверьте настройки: "+e.getMessage());return;}
        ArrayList<String> errors=new ArrayList<>();
        ArrayList<AccountStore.Account> choices=selectedAccounts(errors);
        if(choices.isEmpty()){
            status.setText("Нет выбранных и настроенных ящиков. "+String.join("; ",errors));
            return;
        }
        run.setEnabled(false);test.setEnabled(false);share.setEnabled(false);
        status.setText("Читаем входящие письма Mail.ru...");
        report.setText("");
        Date date=selected.getTime();
        executor.execute(()->{
            ArrayList<ReportEngine.Item> all=new ArrayList<>();
            for(AccountStore.Account a:choices){
                runOnUiThread(()->status.setText("Проверка "+a.login+"..."));
                try{all.addAll(MailReader.fetch(a,date));}
                catch(javax.mail.AuthenticationFailedException e){
                    errors.add(a.login+": ошибка авторизации. Требуется пароль внешнего приложения.");
                }
                catch(Exception e){errors.add(a.login+": ошибка ("+e.getClass().getSimpleName()+")");}
            }
            String result=ReportEngine.build(date,all,errors);
            runOnUiThread(()->{
                lastReport=result;
                report.setText(result);
                status.setText("Готово. Проверено писем: "+all.size()+"; проблем: "+errors.size());
                run.setEnabled(true);test.setEnabled(true);share.setEnabled(true);
            });
        });
    }

    private void shareReport(){
        Intent i=new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT,"Оперативная сводка по Самаре — "+formatter.format(selected.getTime()));
        i.putExtra(Intent.EXTRA_TEXT,lastReport);
        startActivity(Intent.createChooser(i,"Отправить отчёт"));
    }

    @Override protected void onDestroy(){executor.shutdownNow();super.onDestroy();}
}
