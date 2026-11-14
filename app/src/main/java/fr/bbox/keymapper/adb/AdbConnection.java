package fr.bbox.keymapper.adb;

import android.util.Base64;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * Client ADB minimal (protocole ADB sur TCP) : connexion, authentification RSA,
 * ouverture de flux shell et lecture/écriture de données.
 * Référence : protocole ADB (AOSP, adb/protocol.txt et adb_auth).
 */
public class AdbConnection {

    private static final int A_CNXN = 0x4e584e43;
    private static final int A_AUTH = 0x48545541;
    private static final int A_OPEN = 0x4e45504f;
    private static final int A_OKAY = 0x59414b4f;
    private static final int A_CLSE = 0x45534c43;
    private static final int A_WRTE = 0x45545257;

    private static final int AUTH_TOKEN = 1;
    private static final int AUTH_SIGNATURE = 2;
    private static final int AUTH_RSAPUBLICKEY = 3;

    private static final int VERSION = 0x01000000;
    private static final int MAX_DATA = 256 * 1024;

    /** Préfixe DigestInfo SHA-1 utilisé par ADB pour signer le jeton. */
    private static final byte[] SHA1_DIGEST_INFO = hex("3021300906052b0e03021a05000414");

    private final Socket socket;
    private final DataInputStream in;
    private final OutputStream out;
    private final KeyPair keyPair;

    private final Object writeLock = new Object();

    public AdbConnection(String host, int port, KeyPair keyPair) throws IOException {
        this.socket = new Socket(host, port);
        this.in = new DataInputStream(socket.getInputStream());
        this.out = socket.getOutputStream();
        this.keyPair = keyPair;
    }

    /** Envoie CNXN et traite l'authentification jusqu'au CNXN du décodeur. */
    public void handshake() throws IOException {
        send(A_CNXN, VERSION, MAX_DATA, "host::features=shell_v2\0".getBytes("UTF-8"));
        while (true) {
            Message m = read();
            if (m.command == A_CNXN) {
                return;
            }
            if (m.command != A_AUTH) {
                throw new IOException("Réponse inattendue : " + Integer.toHexString(m.command));
            }
            if (m.arg0 == AUTH_TOKEN) {
                if (!sentSignature) {
                    send(A_AUTH, AUTH_SIGNATURE, 0, sign(m.data));
                    sentSignature = true;
                } else {
                    send(A_AUTH, AUTH_RSAPUBLICKEY, 0, publicKeyForAdb());
                }
            }
        }
    }

    private boolean sentSignature = false;

    /** Ouvre un flux shell, par exemple "shell:getevent /dev/input/event7". */
    public void open(int localId, String destination) throws IOException {
        send(A_OPEN, localId, 0, (destination + "\0").getBytes("UTF-8"));
    }

    /** Lit le prochain message reçu du décodeur. */
    public Message read() throws IOException {
        byte[] header = new byte[24];
        in.readFully(header);
        ByteBuffer b = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int command = b.getInt();
        int arg0 = b.getInt();
        int arg1 = b.getInt();
        int length = b.getInt();
        b.getInt(); // checksum (non vérifié)
        b.getInt(); // magic
        byte[] data = new byte[length];
        in.readFully(data);
        return new Message(command, arg0, arg1, data);
    }

    /** Accuse réception d'une donnée WRTE du décodeur. */
    public void ack(int localId, int remoteId) throws IOException {
        send(A_OKAY, localId, remoteId, new byte[0]);
    }

    /** Ferme un flux. */
    public void close(int localId, int remoteId) throws IOException {
        send(A_CLSE, localId, remoteId, new byte[0]);
    }

    public void disconnect() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private void send(int command, int arg0, int arg1, byte[] data) throws IOException {
        int checksum = 0;
        for (byte x : data) {
            checksum += x & 0xff;
        }
        ByteBuffer b = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(command).putInt(arg0).putInt(arg1).putInt(data.length)
                .putInt(checksum).putInt(command ^ 0xffffffff);
        synchronized (writeLock) {
            out.write(b.array());
            out.write(data);
            out.flush();
        }
    }

    private byte[] sign(byte[] token) throws IOException {
        try {
            byte[] toSign = new byte[SHA1_DIGEST_INFO.length + token.length];
            System.arraycopy(SHA1_DIGEST_INFO, 0, toSign, 0, SHA1_DIGEST_INFO.length);
            System.arraycopy(token, 0, toSign, SHA1_DIGEST_INFO.length, token.length);
            Signature sig = Signature.getInstance("NONEwithRSA");
            sig.initSign(keyPair.getPrivate());
            sig.update(toSign);
            return sig.sign();
        } catch (Exception e) {
            throw new IOException("Signature impossible", e);
        }
    }

    /** Clé publique au format ADB (struct RSAPublicKey + base64 + nom). */
    private byte[] publicKeyForAdb() throws IOException {
        RSAPublicKey pub = (RSAPublicKey) keyPair.getPublic();
        BigInteger n = pub.getModulus();
        int words = n.bitLength() / 32;
        BigInteger two32 = BigInteger.ONE.shiftLeft(32);

        BigInteger n0 = n.mod(two32);
        BigInteger n0inv = n0.modInverse(two32).negate().mod(two32);
        BigInteger rr = BigInteger.ONE.shiftLeft(2 * n.bitLength()).mod(n);

        ByteBuffer b = ByteBuffer.allocate(4 + 4 + words * 4 * 2 + 4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(words);
        b.putInt(n0inv.intValue());
        putWords(b, n, words);
        putWords(b, rr, words);
        b.putInt(pub.getPublicExponent().intValue());

        String encoded = Base64.encodeToString(b.array(), Base64.NO_WRAP) + " fr.bbox.keymapper\0";
        return encoded.getBytes("UTF-8");
    }

    private static void putWords(ByteBuffer b, BigInteger value, int words) {
        for (int i = 0; i < words; i++) {
            b.putInt(value.shiftRight(32 * i).intValue());
        }
    }

    /** Charge la paire de clés de l'application, ou la crée au premier lancement. */
    public static KeyPair loadOrCreateKeys(File dir) throws Exception {
        File priv = new File(dir, "adb_private.key");
        File pub = new File(dir, "adb_public.key");
        if (priv.exists() && pub.exists()) {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PrivateKey privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(readAll(priv)));
            PublicKey publicKey = kf.generatePublic(new X509EncodedKeySpec(readAll(pub)));
            return new KeyPair(publicKey, privateKey);
        }
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();
        writeAll(priv, pair.getPrivate().getEncoded());
        writeAll(pub, pair.getPublic().getEncoded());
        return pair;
    }

    private static byte[] readAll(File f) throws IOException {
        byte[] data = new byte[(int) f.length()];
        try (InputStream is = new FileInputStream(f)) {
            new DataInputStream(is).readFully(data);
        }
        return data;
    }

    private static void writeAll(File f, byte[] data) throws IOException {
        try (FileOutputStream os = new FileOutputStream(f)) {
            os.write(data);
        }
    }

    private static byte[] hex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        }
        return out;
    }

    /** Message ADB reçu. */
    public static class Message {
        public final int command;
        public final int arg0;
        public final int arg1;
        public final byte[] data;

        Message(int command, int arg0, int arg1, byte[] data) {
            this.command = command;
            this.arg0 = arg0;
            this.arg1 = arg1;
            this.data = data;
        }
    }
}
