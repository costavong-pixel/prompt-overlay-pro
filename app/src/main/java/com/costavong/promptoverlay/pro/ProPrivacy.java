package com.costavong.promptoverlay.pro;
import android.app.AlertDialog;
import android.content.Context;
import com.costavong.promptoverlay.AppLanguage;
public final class ProPrivacy {
    public static void show(Context context){String message;
        switch(AppLanguage.current(context)){
            case "es":message="Los guiones y proyectos se guardan en este teléfono. Los vídeos, imágenes y música importados se copian al almacenamiento privado de la app; los originales no se modifican. Los subtítulos se crean en el teléfono. La primera vez se descarga un paquete de voz del servidor público de Vosk, que recibe los datos normales de conexión. No se envía tu vídeo ni audio a un servicio de transcripción. Puedes guardar o compartir un vídeo terminado mediante Android; la app que elijas lo recibirá. No hay anuncios, analítica, cuenta, ni acceso a cámara o micrófono. El control remoto del teleprompter usa la red local.";break;
            case "fr":message="Les scripts et projets restent sur ce téléphone. Les vidéos, images et musiques importées sont copiées dans le stockage privé de l’application ; les originaux ne sont pas modifiés. Les sous-titres sont créés sur le téléphone. À la première utilisation, un pack vocal est téléchargé depuis le serveur public de Vosk, qui reçoit les informations de connexion habituelles. Vos vidéos et votre son ne sont pas envoyés à un service de transcription. Vous pouvez enregistrer ou partager une vidéo via Android ; l’application choisie la recevra. Aucun compte, publicité, analyse d’usage, accès à la caméra ou au microphone. La télécommande du téléprompteur utilise le réseau local.";break;
            case "ar":message="تبقى النصوص والمشاريع على هذا الهاتف. تُنسخ الفيديوهات والصور والموسيقى المستوردة إلى مساحة التطبيق الخاصة دون تعديل الملفات الأصلية. تُنشأ الترجمة النصية على الهاتف. عند الاستخدام الأول، تُنزّل حزمة كلام من خادم Vosk العام الذي يتلقى معلومات الاتصال المعتادة. لا يُرسل الفيديو أو الصوت إلى خدمة تفريغ. يمكنك حفظ فيديو مكتمل أو مشاركته عبر Android؛ وسيستلمه التطبيق الذي تختاره. لا توجد إعلانات أو تحليلات استخدام أو حسابات أو وصول إلى الكاميرا أو الميكروفون. يستخدم جهاز التحكم في الملقّن الشبكة المحلية.";break;
            default:message="Scripts and projects stay on this phone. Imported videos, images and music are copied into the app’s private storage; originals are not changed. Captions are created on the phone. The first use downloads a speech pack from Vosk’s public server, which receives normal connection information. Your video and audio are not sent to a transcription service. You can save or share a finished video using Android; your chosen app then receives that file. There are no ads, usage analytics, accounts, or camera/microphone access. The teleprompter’s remote uses your local network.";
        }
        new AlertDialog.Builder(context).setTitle(ProStrings.t(context,"Privacy")).setMessage(message).setPositiveButton(ProStrings.t(context,"Close"),null).show();
    }
}
