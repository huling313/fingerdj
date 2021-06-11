package cn.org.bjca.szyx.patient.sign.fingerdj.finger;


import com.seuic.fingerprint.FingerprintService;

public class FingerManage {

    public static FingerprintService fingerprintService = null;

    public static boolean openDevice() {
        if (null == fingerprintService) {
            fingerprintService = FingerprintService.getInstance();
        }
        return fingerprintService.open();
    }

    public static void closeDevice() {
        if (null != fingerprintService) {
            fingerprintService.close();
        }
    }
}
