package com.costavong.promptoverlay.pro;

import android.content.Context;
import com.costavong.promptoverlay.AppLanguage;

/** Localized copy used only by the private product feedback form. */
final class FeedbackStrings {
    private static final String[][] WORDS={
        {"Teleprompter","Teleprompter","Téléprompteur","الملقّن"},
        {"Back","Atrás","Retour","رجوع"},
        {"Win a FREE Lifetime License from Us","Gana una licencia GRATIS de por vida","Gagnez une licence à vie GRATUITE","احصل على فرصة للفوز بترخيص مجاني مدى الحياة"},
        {"We will choose 500 users to receive a FREE Pro Lifetime license, based on the quality of their private product feedback.","Elegiremos a 500 usuarios para recibir una licencia Pro de por vida GRATIS según la calidad de sus comentarios privados sobre el producto.","Nous choisirons 500 utilisateurs qui recevront une licence Pro à vie GRATUITE selon la qualité de leurs retours privés sur le produit.","سنختار 500 مستخدم للحصول على ترخيص Pro مجاني مدى الحياة بناءً على جودة ملاحظاتهم الخاصة عن المنتج."},
        {"Only detailed, specific, useful feedback will be considered. Explain what you tried, what happened, and how to reproduce the issue or improve the feature. Photos, short videos, and links are welcome.","Solo consideraremos comentarios detallados, específicos y útiles. Explica qué probaste, qué ocurrió y cómo reproducir el error o mejorar la función. Puedes añadir fotos, vídeos cortos o enlaces.","Seuls les retours détaillés, précis et utiles seront examinés. Expliquez ce que vous avez essayé, ce qui s’est passé et comment reproduire le problème ou améliorer la fonction. Photos, courtes vidéos et liens acceptés.","سننظر فقط في الملاحظات المفصلة والمحددة والمفيدة. اشرح ما جربته وما حدث وكيفية إعادة المشكلة أو تحسين الميزة. يمكنك إرفاق صور أو فيديوهات قصيرة أو روابط."},
        {"Public Google Play ratings or reviews are not required and do not affect selection. Submitting feedback does not guarantee selection.","No es necesario publicar reseñas ni valoraciones en Google Play, y no afectan la selección. Enviar comentarios no garantiza ser elegido.","Les avis et notes publics sur Google Play ne sont pas obligatoires et n’influencent pas la sélection. L’envoi d’un retour ne garantit pas la sélection.","لا يشترط نشر تقييمات أو مراجعات على Google Play ولا تؤثر في الاختيار. إرسال الملاحظات لا يضمن الاختيار."},
        {"Explain the result and what you expected.","Explica qué ocurrió y qué esperabas.","Expliquez le résultat obtenu et celui attendu.","اشرح النتيجة التي ظهرت وما كنت تتوقعه."},
        {"Share a video or screenshot link (optional)","Comparte un enlace al vídeo o captura (opcional)","Partagez un lien vidéo ou capture (facultatif)","شارك رابط فيديو أو لقطة شاشة (اختياري)"},
        {"https://drive.google.com/... or another shareable link","https://drive.google.com/... u otro enlace compartible","https://drive.google.com/... ou autre lien partageable","https://drive.google.com/... أو رابط آخر قابل للمشاركة"},
        {"Only add media you agree to share. For large videos, paste a cloud link with view permission instead. The app does not upload files automatically.","Adjunta solo archivos que quieras compartir. Para vídeos grandes, pega un enlace en la nube con permiso de visualización. La app no sube archivos automáticamente.","N’ajoutez que des médias que vous acceptez de partager. Pour les grandes vidéos, collez un lien cloud avec accès en lecture. L’application n’envoie aucun fichier automatiquement.","أرفق فقط الملفات التي توافق على مشاركتها. للفيديوهات الكبيرة، الصق رابطًا سحابيًا يسمح بالمشاهدة. لا يرفع التطبيق الملفات تلقائيًا."},
        {"Attach photos or videos","Adjuntar fotos o vídeos","Joindre des photos ou vidéos","إرفاق صور أو فيديوهات"},
        {"Submitting opens your email app. Review the message and attachments, then send it yourself. Do not include passwords or sensitive personal information.","Se abrirá tu app de correo. Revisa el mensaje y los adjuntos antes de enviarlo. No incluyas contraseñas ni datos personales sensibles.","Votre messagerie s’ouvrira. Vérifiez le message et les pièces jointes avant de l’envoyer. N’incluez pas de mots de passe ni de données sensibles.","سيفتح تطبيق بريدك الإلكتروني. راجع الرسالة والمرفقات ثم أرسلها بنفسك. لا تضف كلمات مرور أو معلومات شخصية حساسة."},
        {"Selected evidence: ","Archivos seleccionados: ","Fichiers sélectionnés : ","الملفات المختارة: "},
        {" of 4 files (15 MB maximum total)"," de 4 archivos (15 MB en total como máximo)"," sur 4 fichiers (15 Mo au total maximum)"," من 4 ملفات (الحد الأقصى 15 ميغابايت)"},
        {"Remove","Quitar","Retirer","إزالة"},
        {"Please select a photo or video.","Selecciona una foto o un vídeo.","Veuillez choisir une photo ou une vidéo.","يرجى اختيار صورة أو فيديو."},
        {"Up to four photos/videos (15 MB total). For large videos, share a link.","Máximo 4 fotos o vídeos (15 MB en total). Para vídeos grandes, comparte un enlace.","Jusqu’à 4 photos/vidéos (15 Mo au total). Pour les grandes vidéos, partagez un lien.","يمكن إرفاق 4 صور أو فيديوهات بحد أقصى 15 ميغابايت. للفيديوهات الكبيرة شارك رابطًا."},
        {"Unable to open photo/video picker.","No se pudo abrir el selector de fotos y vídeos.","Impossible d’ouvrir le sélecteur de photos et vidéos.","تعذر فتح منتقي الصور والفيديوهات."},
        {"Please explain what you tried, the outcome and exact steps or a useful suggestion.","Explica qué probaste, el resultado y los pasos exactos o una sugerencia útil.","Expliquez ce que vous avez essayé, le résultat et les étapes exactes ou une suggestion utile.","اشرح ما جربته والنتيجة والخطوات الدقيقة أو اقتراحًا مفيدًا."},
        {"Please enter an http(s) shareable link, or leave the link empty.","Introduce un enlace compartible http(s) o deja el campo vacío.","Saisissez un lien http(s) partageable ou laissez le champ vide.","أدخل رابطًا قابلًا للمشاركة يبدأ بـ http(s) أو اترك الحقل فارغًا."},
        {"No compatible email app. Email support@barndai.com or use a share link.","No hay app de correo compatible. Escribe a support@barndai.com o comparte un enlace.","Aucune messagerie compatible. Écrivez à support@barndai.com ou partagez un lien.","لا يوجد تطبيق بريد متوافق. راسل support@barndai.com أو استخدم رابط مشاركة."},
        {"Feature you tried","Función que probaste","Fonction essayée","الميزة التي جربتها"},
        {"What did you try?","¿Qué probaste?","Qu’avez-vous essayé ?","ماذا جربت؟"},
        {"What happened? What should improve?","¿Qué pasó? ¿Qué debería mejorar?","Que s’est-il passé ? Que faut-il améliorer ?","ماذا حدث؟ ما الذي يجب تحسينه؟"},
        {"Steps to reproduce or an actionable suggestion","Pasos para reproducir el problema o sugerencia concreta","Étapes pour reproduire le problème ou suggestion concrète","خطوات إعادة المشكلة أو اقتراح عملي"},
        {"Continue to email","Continuar al correo","Continuer vers l’e-mail","المتابعة إلى البريد الإلكتروني"},
        {"Usability","Facilidad de uso","Ergonomie","سهولة الاستخدام"},
        {"Other","Otro","Autre","أخرى"},
        {"Script library","Biblioteca de guiones","Bibliothèque de scripts","مكتبة النصوص"},
        {"Import","Importar","Importer","استيراد"},
        {"Remote controls","Controles remotos","Télécommandes","أدوات التحكم عن بُعد"},
        {"Example: Saved a script and adjusted its scroll speed.","Ejemplo: guardé un guion y ajusté la velocidad de desplazamiento.","Exemple : j’ai enregistré un script et réglé la vitesse de défilement.","مثال: حفظت نصًا وضبطت سرعة التمرير."},
        {"Example: Open a script, start the overlay, and connect a remote.","Ejemplo: abre un guion, inicia la superposición y conecta un mando.","Exemple : ouvrez un script, démarrez l’incrustation et connectez une télécommande.","مثال: افتح نصًا وشغّل النافذة العائمة ووصل جهاز تحكم."},
    };

    private FeedbackStrings(){}

    static String t(Context context,String source){
        String code=AppLanguage.current(context);
        int column="es".equals(code)?1:"fr".equals(code)?2:"ar".equals(code)?3:0;
        for(String[] row:WORDS)if(row[0].equals(source))return row[column];
        return AppLanguage.t(context,source);
    }
}
