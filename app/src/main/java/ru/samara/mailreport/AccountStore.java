package ru.samara.mailreport;
import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
final class AccountStore {
    static final class Account {
        String title, host, login, secret; int port;
        Account(String title, String host, int port, String login, String secret) {
            this.title=title;this.host=host;this.port=port;this.login=login;this.secret=secret;
        }
    }
    private static final String KEY="samara_mail_account_key";
    private final SharedPreferences prefs;
    AccountStore(Context ctx) { prefs=ctx.getSharedPreferences("accounts",Context.MODE_PRIVATE); }
    private SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if (!ks.containsAlias(KEY)) {
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(KEY,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        return ((KeyStore.SecretKeyEntry)ks.getEntry(KEY,null)).getSecretKey();
    }
    private String encrypt(String value) throws Exception {
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());
        byte[] data=c.doFinal(value.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(c.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(data,Base64.NO_WRAP);
    }
    private String decrypt(String value) throws Exception {
        String[] parts=value.split(":",2);
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));
        return new String(c.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);
    }
    ArrayList<Account> read() throws Exception {
        ArrayList<Account> list=new ArrayList<>(); JSONArray arr=new JSONArray(prefs.getString("items","[]"));
        for(int i=0;i<arr.length();i++){
            JSONObject o=arr.getJSONObject(i);list.add(new Account(o.getString("title"),o.getString("host"),o.getInt("port"),o.getString("login"),decrypt(o.getString("secret"))));
        }
        return list;
    }
    void save(ArrayList<Account> list) throws Exception {
        JSONArray arr=new JSONArray();for(Account a:list){JSONObject o=new JSONObject();
            o.put("title",a.title);o.put("host",a.host);o.put("port",a.port);o.put("login",a.login);o.put("secret",encrypt(a.secret));arr.put(o);}
        if(!prefs.edit().putString("items",arr.toString()).commit())throw new IllegalStateException("Не удалось сохранить ящики");
    }
}
