package com.costavong.promptoverlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;

/**
 * Small, local UI-language helper. Script content is never translated or changed;
 * this setting only changes the application controls and their layout direction.
 */
public final class AppLanguage {
    public static final String ENGLISH = "en";
    public static final String SPANISH = "es";
    public static final String ARABIC = "ar";
    public static final String FRENCH = "fr";

    private static final String PREFERENCES = "prompt_overlay";
    private static final String KEY_LANGUAGE = "app_language";

    private AppLanguage() {
        // Utility class.
    }

    public static String current(Context context) {
        String value = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE, ENGLISH);
        if (SPANISH.equals(value) || ARABIC.equals(value) || FRENCH.equals(value)) {
            return value;
        }
        return ENGLISH;
    }

    public static void set(Context context, String language) {
        String safeLanguage = SPANISH.equals(language) || ARABIC.equals(language)
                || FRENCH.equals(language) ? language : ENGLISH;
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LANGUAGE, safeLanguage)
                .apply();
    }

    public static boolean isRtl(Context context) {
        return ARABIC.equals(current(context));
    }

    public static int layoutDirection(Context context) {
        return isRtl(context) ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR;
    }

    public static String[] languageLabels() {
        return new String[]{"English", "Español", "العربية", "Français"};
    }

    public static String codeAt(int index) {
        switch (index) {
            case 1:
                return SPANISH;
            case 2:
                return ARABIC;
            case 3:
                return FRENCH;
            default:
                return ENGLISH;
        }
    }

    public static int indexOf(Context context) {
        String language = current(context);
        if (SPANISH.equals(language)) return 1;
        if (ARABIC.equals(language)) return 2;
        if (FRENCH.equals(language)) return 3;
        return 0;
    }

    /** Returns a translated UI string when it is a supported product label. */
    public static String t(Context context, String source) {
        if (source == null || ENGLISH.equals(current(context))) {
            return source;
        }
        String language = current(context);
        if (SPANISH.equals(language)) {
            return spanish(source);
        }
        if (ARABIC.equals(language)) {
            return arabic(source);
        }
        return french(source);
    }

    private static String spanish(String source) {
        switch (source) {
            case "Script": return "Guion";
            case "Import": return "Importar";
            case "Appearance": return "Apariencia";
            case "Remote Control": return "Control remoto";
            case "Prompt Controls": return "Controles del apuntador";
            case "Saved scripts": return "Guiones guardados";
            case "Script library": return "Biblioteca de guiones";
            case "Open script library": return "Abrir biblioteca de guiones";
            case "Back to scripts": return "Volver a los guiones";
            case "New script": return "Nuevo guion";
            case "Save changes": return "Guardar cambios";
            case "Save & new": return "Guardar y crear otro";
            case "Script title": return "Título del guion";
            case "Tags (optional)": return "Etiquetas (opcionales)";
            case "Your script": return "Tu guion";
            case "Search scripts": return "Buscar guiones";
            case "All": return "Todos";
            case "Active": return "Activos";
            case "Archived": return "Archivados";
            case "Archive": return "Archivar";
            case "Restore": return "Restaurar";
            case "Delete": return "Eliminar";
            case "Cancel": return "Cancelar";
            case "Current": return "Actual";
            case "No tags": return "Sin etiquetas";
            case "Reading font": return "Fuente de lectura";
            case "Prompt text colour": return "Color del texto";
            case "Reading alignment": return "Alineación de lectura";
            case "App language": return "Idioma de la aplicación";
            case "Import from Google Drive": return "Importar desde Google Drive";
            case "Import from Files or Photo": return "Importar desde archivos o foto";
            case "Bluetooth connect (remote control)": return "Conectar Bluetooth (control remoto)";
            case "Wi-Fi connect (remote control) — 3 steps": return "Conectar por Wi-Fi (control remoto) — 3 pasos";
            case "Turn on Wi-Fi remote": return "Activar control remoto por Wi-Fi";
            case "Connect custom Prompt Remote (optional)": return "Conectar Prompt Remote personalizado (opcional)";
            case "Start floating prompt": return "Iniciar apuntador flotante";
            case "Stop prompt": return "Detener apuntador";
            case "Allow floating prompt": return "Permitir apuntador flotante";
            case "Auto-scroll speed": return "Velocidad de desplazamiento";
            case "Floating prompt": return "Apuntador flotante";
            case "Height overlay (max 75%)": return "Altura del superpuesto (máx. 75%)";
            case "Width overlay": return "Ancho del superpuesto";
            case "Prompt text size": return "Tamaño del texto";
            case "Prompt background": return "Fondo del apuntador";
            case "Reading layout": return "Diseño de lectura";
            case "Reading side margin": return "Margen lateral";
            case "Line spacing": return "Espaciado entre líneas";
            case "Start countdown": return "Cuenta atrás";
            case "Open mirrored full-screen": return "Abrir modo espejo";
            case "Privacy": return "Privacidad";
            case "Roadmap": return "Plan de producto";
            case "Rate": return "Valorar";
            case "Close": return "Cerrar";
            case "Done": return "Listo";
            case "Clear": return "Borrar";
            case "Left": return "Izquierda";
            case "Centre": return "Centro";
            case "Right": return "Derecha";
            case "White": return "Blanco";
            case "Warm white": return "Blanco cálido";
            case "Yellow": return "Amarillo";
            case "Mint": return "Menta";
            case "Cyan": return "Cian";
            case "Pink": return "Rosa";
            case "Test build · not for sale": return "Versión de prueba · no a la venta";
            case "Your saved scripts stay on this phone. Open the library to search and manage them.": return "Tus guiones guardados permanecen en este teléfono. Abre la biblioteca para buscarlos y gestionarlos.";
            case "Save changes updates this script. Save & new keeps it and starts another one.": return "Guardar cambios actualiza este guion. Guardar y crear otro lo conserva e inicia uno nuevo.";
            case "Example: Monday product video": return "Ejemplo: vídeo de producto del lunes";
            case "e.g. UGC, launch, draft": return "p. ej., UGC, lanzamiento, borrador";
            case "Paste your script here. Short lines are easier to read while recording.": return "Pega aquí tu guion. Las líneas cortas son más fáciles de leer al grabar.";
            case "Select words, then use the text-style bar below. It works on the title, tags, or script you are editing.": return "Selecciona palabras y usa la barra de estilo de texto. Funciona en el título, las etiquetas o el guion que editas.";
            case "Opens the Google Drive app. Choose a document from your signed-in Drive; Prompt Overlay only reads the file you choose.": return "Abre la aplicación Google Drive. Elige un documento de tu Drive; Prompt Overlay solo lee el archivo que elijas.";
            case "Choose a document or photo/screenshot. Documents and OCR are processed on this phone.": return "Elige un documento o una foto/captura. Los documentos y el OCR se procesan en este teléfono.";
            case "Works with standard Bluetooth or USB keyboard, clicker, foot pedal, media, and gamepad controls. You can set your own buttons.": return "Funciona con teclado Bluetooth o USB, clic remoto, pedal, controles multimedia y de mando. Puedes asignar tus propios botones.";
            case "The address and code appear only after the prompt starts.": return "La dirección y el código aparecen solo después de iniciar el apuntador.";
            case "Repeat silently when prompt ends": return "Repetir en silencio al terminar";
            case "Set the starting panel here. In Camera, drag the handle near the lens or pinch it to resize.": return "Configura aquí el panel inicial. En Cámara, arrastra el control cerca de la lente o pellizca para cambiar el tamaño.";
            case "Use only with angled teleprompter glass. The reflected script will read normally.": return "Úsalo solo con vidrio de teleprónter inclinado. El guion reflejado se leerá normalmente.";
            case "Tap a card to edit it. Use the menu on a card to archive or delete it.": return "Toca una tarjeta para editarla. Usa el menú de la tarjeta para archivarla o eliminarla.";
            case "Save a script to build your local library.": return "Guarda un guion para crear tu biblioteca local.";
            case "No scripts match this filter.": return "Ningún guion coincide con este filtro.";
            case "More": return "Más";
            case "Delete script?": return "¿Eliminar el guion?";
            case "will be removed from this phone.": return "se eliminará de este teléfono.";
            case "Write a script before saving it.": return "Escribe un guion antes de guardarlo.";
            case "Script saved on this phone.": return "Guion guardado en este teléfono.";
            case "New script ready.": return "Nuevo guion listo.";
            case "Bluetooth & hardware remote": return "Bluetooth y control físico";
            case "Custom Bluetooth remote": return "Control Bluetooth personalizado";
            case "Remote controls": return "Controles remotos";
            case "Reset to standard controls": return "Restablecer controles estándar";
            case "Set button": return "Asignar botón";
            case "Listening — press remote button": return "Escuchando — pulsa un botón remoto";
            case "Scan for custom remote": return "Buscar control personalizado";
            case "Found remotes": return "Controles encontrados";
            case "Ready to scan.": return "Listo para buscar.";
            case "Pause": return "Pausar";
            case "Replay": return "Repetir";
            case "Start": return "Iniciar";
            case "Top": return "Inicio";
            case "Starts in": return "Empieza en";
            case "Drag prompt  •  Pinch to resize": return "Arrastra el apuntador  •  Pellizca para cambiar el tamaño";
            case "No script yet. Open Prompt Overlay and paste your words.": return "Aún no hay guion. Abre Prompt Overlay y pega tus palabras.";
            case "No script yet. Return and paste your words.": return "Aún no hay guion. Vuelve y pega tus palabras.";
            case "Mirror full-screen  •  Script is reversed  •  Hardware remote ready": return "Espejo a pantalla completa  •  Guion invertido  •  Control físico listo";
            case "Off": return "Desactivado";
            case "slow": return "lento";
            case "medium": return "medio";
            case "fast": return "rápido";
            case "Test build · unlimited prompt time · no purchase flow": return "Versión de prueba · tiempo ilimitado · sin compras";
            default: return source;
        }
    }

    private static String french(String source) {
        switch (source) {
            case "Script": return "Script";
            case "Import": return "Importer";
            case "Appearance": return "Apparence";
            case "Remote Control": return "Télécommande";
            case "Prompt Controls": return "Commandes du prompteur";
            case "Saved scripts": return "Scripts enregistrés";
            case "Script library": return "Bibliothèque de scripts";
            case "Open script library": return "Ouvrir la bibliothèque";
            case "Back to scripts": return "Retour aux scripts";
            case "New script": return "Nouveau script";
            case "Save changes": return "Enregistrer les modifications";
            case "Save & new": return "Enregistrer et créer";
            case "Script title": return "Titre du script";
            case "Tags (optional)": return "Étiquettes (facultatives)";
            case "Your script": return "Votre script";
            case "Search scripts": return "Rechercher des scripts";
            case "All": return "Tous";
            case "Active": return "Actifs";
            case "Archived": return "Archivés";
            case "Archive": return "Archiver";
            case "Restore": return "Restaurer";
            case "Delete": return "Supprimer";
            case "Cancel": return "Annuler";
            case "Current": return "Actuel";
            case "No tags": return "Sans étiquette";
            case "Reading font": return "Police de lecture";
            case "Prompt text colour": return "Couleur du texte";
            case "Reading alignment": return "Alignement de lecture";
            case "App language": return "Langue de l’application";
            case "Import from Google Drive": return "Importer depuis Google Drive";
            case "Import from Files or Photo": return "Importer depuis Fichiers ou Photo";
            case "Bluetooth connect (remote control)": return "Connexion Bluetooth (télécommande)";
            case "Wi-Fi connect (remote control) — 3 steps": return "Connexion Wi-Fi (télécommande) — 3 étapes";
            case "Turn on Wi-Fi remote": return "Activer la télécommande Wi-Fi";
            case "Connect custom Prompt Remote (optional)": return "Connecter une Prompt Remote personnalisée (facultatif)";
            case "Start floating prompt": return "Démarrer le prompteur flottant";
            case "Stop prompt": return "Arrêter le prompteur";
            case "Allow floating prompt": return "Autoriser le prompteur flottant";
            case "Auto-scroll speed": return "Vitesse de défilement";
            case "Floating prompt": return "Prompteur flottant";
            case "Height overlay (max 75%)": return "Hauteur du prompteur (max. 75 %)";
            case "Width overlay": return "Largeur du prompteur";
            case "Prompt text size": return "Taille du texte";
            case "Prompt background": return "Arrière-plan du prompteur";
            case "Reading layout": return "Mise en page de lecture";
            case "Reading side margin": return "Marge latérale";
            case "Line spacing": return "Interligne";
            case "Start countdown": return "Compte à rebours";
            case "Open mirrored full-screen": return "Ouvrir le mode miroir";
            case "Privacy": return "Confidentialité";
            case "Roadmap": return "Feuille de route";
            case "Rate": return "Noter";
            case "Close": return "Fermer";
            case "Done": return "Terminé";
            case "Clear": return "Effacer";
            case "Left": return "Gauche";
            case "Centre": return "Centre";
            case "Right": return "Droite";
            case "White": return "Blanc";
            case "Warm white": return "Blanc chaud";
            case "Yellow": return "Jaune";
            case "Mint": return "Menthe";
            case "Cyan": return "Cyan";
            case "Pink": return "Rose";
            case "Test build · not for sale": return "Version de test · non destinée à la vente";
            case "Your saved scripts stay on this phone. Open the library to search and manage them.": return "Vos scripts enregistrés restent sur ce téléphone. Ouvrez la bibliothèque pour les rechercher et les gérer.";
            case "Save changes updates this script. Save & new keeps it and starts another one.": return "Enregistrer les modifications met à jour ce script. Enregistrer et créer le conserve et en démarre un autre.";
            case "Example: Monday product video": return "Exemple : vidéo produit du lundi";
            case "e.g. UGC, launch, draft": return "ex. UGC, lancement, brouillon";
            case "Paste your script here. Short lines are easier to read while recording.": return "Collez votre script ici. Les lignes courtes sont plus faciles à lire pendant l’enregistrement.";
            case "Select words, then use the text-style bar below. It works on the title, tags, or script you are editing.": return "Sélectionnez des mots, puis utilisez la barre de style. Elle fonctionne pour le titre, les étiquettes ou le script que vous modifiez.";
            case "Opens the Google Drive app. Choose a document from your signed-in Drive; Prompt Overlay only reads the file you choose.": return "Ouvre l’application Google Drive. Choisissez un document dans votre Drive ; Prompt Overlay lit uniquement le fichier choisi.";
            case "Choose a document or photo/screenshot. Documents and OCR are processed on this phone.": return "Choisissez un document ou une photo/capture. Les documents et l’OCR sont traités sur ce téléphone.";
            case "Works with standard Bluetooth or USB keyboard, clicker, foot pedal, media, and gamepad controls. You can set your own buttons.": return "Fonctionne avec un clavier Bluetooth ou USB, un clicker, une pédale, des commandes média et une manette. Vous pouvez choisir vos boutons.";
            case "The address and code appear only after the prompt starts.": return "L’adresse et le code apparaissent seulement après le démarrage du prompteur.";
            case "Repeat silently when prompt ends": return "Répéter silencieusement à la fin";
            case "Set the starting panel here. In Camera, drag the handle near the lens or pinch it to resize.": return "Réglez ici le panneau de départ. Dans Caméra, faites glisser la poignée près de l’objectif ou pincez pour redimensionner.";
            case "Use only with angled teleprompter glass. The reflected script will read normally.": return "À utiliser uniquement avec un verre de téléprompteur incliné. Le script réfléchi sera lisible normalement.";
            case "Tap a card to edit it. Use the menu on a card to archive or delete it.": return "Touchez une carte pour la modifier. Utilisez le menu d’une carte pour l’archiver ou la supprimer.";
            case "Save a script to build your local library.": return "Enregistrez un script pour créer votre bibliothèque locale.";
            case "No scripts match this filter.": return "Aucun script ne correspond à ce filtre.";
            case "More": return "Plus";
            case "Delete script?": return "Supprimer le script ?";
            case "will be removed from this phone.": return "sera supprimé de ce téléphone.";
            case "Write a script before saving it.": return "Écrivez un script avant de l’enregistrer.";
            case "Script saved on this phone.": return "Script enregistré sur ce téléphone.";
            case "New script ready.": return "Nouveau script prêt.";
            case "Bluetooth & hardware remote": return "Bluetooth et télécommande matérielle";
            case "Custom Bluetooth remote": return "Télécommande Bluetooth personnalisée";
            case "Remote controls": return "Commandes à distance";
            case "Reset to standard controls": return "Réinitialiser les commandes standard";
            case "Set button": return "Définir le bouton";
            case "Listening — press remote button": return "Écoute — appuyez sur un bouton";
            case "Scan for custom remote": return "Rechercher une télécommande personnalisée";
            case "Found remotes": return "Télécommandes trouvées";
            case "Ready to scan.": return "Prêt à rechercher.";
            case "Pause": return "Pause";
            case "Replay": return "Rejouer";
            case "Start": return "Démarrer";
            case "Top": return "Début";
            case "Starts in": return "Démarre dans";
            case "Drag prompt  •  Pinch to resize": return "Faites glisser le prompteur  •  Pincez pour redimensionner";
            case "No script yet. Open Prompt Overlay and paste your words.": return "Aucun script. Ouvrez Prompt Overlay et collez votre texte.";
            case "No script yet. Return and paste your words.": return "Aucun script. Revenez en arrière et collez votre texte.";
            case "Mirror full-screen  •  Script is reversed  •  Hardware remote ready": return "Miroir plein écran  •  Script inversé  •  Télécommande matérielle prête";
            case "Off": return "Désactivé";
            case "slow": return "lent";
            case "medium": return "moyen";
            case "fast": return "rapide";
            case "Test build · unlimited prompt time · no purchase flow": return "Version de test · temps illimité · aucun achat";
            default: return source;
        }
    }

    private static String arabic(String source) {
        switch (source) {
            case "Script": return "النص";
            case "Import": return "استيراد";
            case "Appearance": return "المظهر";
            case "Remote Control": return "التحكم عن بُعد";
            case "Prompt Controls": return "عناصر التحكم في الملقّن";
            case "Saved scripts": return "النصوص المحفوظة";
            case "Script library": return "مكتبة النصوص";
            case "Open script library": return "فتح مكتبة النصوص";
            case "Back to scripts": return "العودة إلى النصوص";
            case "New script": return "نص جديد";
            case "Save changes": return "حفظ التغييرات";
            case "Save & new": return "حفظ وإنشاء نص جديد";
            case "Script title": return "عنوان النص";
            case "Tags (optional)": return "الوسوم (اختياري)";
            case "Your script": return "النص الخاص بك";
            case "Search scripts": return "البحث في النصوص";
            case "All": return "الكل";
            case "Active": return "النشطة";
            case "Archived": return "المؤرشفة";
            case "Archive": return "أرشفة";
            case "Restore": return "استعادة";
            case "Delete": return "حذف";
            case "Cancel": return "إلغاء";
            case "Current": return "الحالي";
            case "No tags": return "بلا وسوم";
            case "Reading font": return "خط القراءة";
            case "Prompt text colour": return "لون النص";
            case "Reading alignment": return "محاذاة القراءة";
            case "App language": return "لغة التطبيق";
            case "Import from Google Drive": return "استيراد من Google Drive";
            case "Import from Files or Photo": return "استيراد من الملفات أو صورة";
            case "Bluetooth connect (remote control)": return "اتصال Bluetooth (تحكم عن بُعد)";
            case "Wi-Fi connect (remote control) — 3 steps": return "اتصال Wi‑Fi (تحكم عن بُعد) — 3 خطوات";
            case "Turn on Wi-Fi remote": return "تشغيل التحكم عن بُعد عبر Wi‑Fi";
            case "Connect custom Prompt Remote (optional)": return "اتصال بوحدة Prompt Remote مخصصة (اختياري)";
            case "Start floating prompt": return "بدء الملقّن العائم";
            case "Stop prompt": return "إيقاف الملقّن";
            case "Allow floating prompt": return "السماح بالملقّن العائم";
            case "Auto-scroll speed": return "سرعة التمرير التلقائي";
            case "Floating prompt": return "الملقّن العائم";
            case "Height overlay (max 75%)": return "ارتفاع التراكب (حد أقصى 75٪)";
            case "Width overlay": return "عرض التراكب";
            case "Prompt text size": return "حجم النص";
            case "Prompt background": return "خلفية الملقّن";
            case "Reading layout": return "تخطيط القراءة";
            case "Reading side margin": return "الهامش الجانبي";
            case "Line spacing": return "تباعد الأسطر";
            case "Start countdown": return "العد التنازلي";
            case "Open mirrored full-screen": return "فتح وضع المرآة";
            case "Privacy": return "الخصوصية";
            case "Roadmap": return "خارطة الطريق";
            case "Rate": return "تقييم";
            case "Close": return "إغلاق";
            case "Done": return "تم";
            case "Clear": return "مسح";
            case "Left": return "يسار";
            case "Centre": return "وسط";
            case "Right": return "يمين";
            case "White": return "أبيض";
            case "Warm white": return "أبيض دافئ";
            case "Yellow": return "أصفر";
            case "Mint": return "نعناعي";
            case "Cyan": return "سماوي";
            case "Pink": return "وردي";
            case "Test build · not for sale": return "إصدار تجريبي · غير مخصّص للبيع";
            case "Your saved scripts stay on this phone. Open the library to search and manage them.": return "تبقى النصوص المحفوظة على هذا الهاتف. افتح المكتبة للبحث عنها وإدارتها.";
            case "Save changes updates this script. Save & new keeps it and starts another one.": return "حفظ التغييرات يحدّث هذا النص. الحفظ وإنشاء نص جديد يحتفظ به ويبدأ نصًا آخر.";
            case "Example: Monday product video": return "مثال: فيديو منتج يوم الاثنين";
            case "e.g. UGC, launch, draft": return "مثل: UGC، إطلاق، مسودة";
            case "Paste your script here. Short lines are easier to read while recording.": return "ألصق النص هنا. الأسطر القصيرة أسهل في القراءة أثناء التسجيل.";
            case "Select words, then use the text-style bar below. It works on the title, tags, or script you are editing.": return "حدّد الكلمات، ثم استخدم شريط تنسيق النص أدناه. يعمل مع العنوان والوسوم والنص الذي تحرّره.";
            case "Opens the Google Drive app. Choose a document from your signed-in Drive; Prompt Overlay only reads the file you choose.": return "يفتح تطبيق Google Drive. اختر مستندًا من Drive الخاص بك؛ يقرأ Prompt Overlay الملف الذي تختاره فقط.";
            case "Choose a document or photo/screenshot. Documents and OCR are processed on this phone.": return "اختر مستندًا أو صورة/لقطة شاشة. تُعالج المستندات وميزة OCR على هذا الهاتف.";
            case "Works with standard Bluetooth or USB keyboard, clicker, foot pedal, media, and gamepad controls. You can set your own buttons.": return "يعمل مع لوحة مفاتيح Bluetooth أو USB ونقّار ودواسة قدم ووسائط ووحدة تحكم. يمكنك ضبط أزرارك الخاصة.";
            case "The address and code appear only after the prompt starts.": return "يظهر العنوان والرمز فقط بعد بدء الملقّن.";
            case "Repeat silently when prompt ends": return "التكرار بصمت عند انتهاء الملقّن";
            case "Set the starting panel here. In Camera, drag the handle near the lens or pinch it to resize.": return "اضبط اللوحة الأولية هنا. في الكاميرا، اسحب المقبض قرب العدسة أو استخدم إصبعين لتغيير الحجم.";
            case "Use only with angled teleprompter glass. The reflected script will read normally.": return "استخدمه فقط مع زجاج ملقّن مائل. سيُقرأ النص المنعكس بشكل طبيعي.";
            case "Tap a card to edit it. Use the menu on a card to archive or delete it.": return "اضغط على بطاقة لتحريرها. استخدم قائمة البطاقة لأرشفتها أو حذفها.";
            case "Save a script to build your local library.": return "احفظ نصًا لإنشاء مكتبتك المحلية.";
            case "No scripts match this filter.": return "لا توجد نصوص مطابقة لهذا الفلتر.";
            case "More": return "المزيد";
            case "Delete script?": return "حذف النص؟";
            case "will be removed from this phone.": return "ستتم إزالته من هذا الهاتف.";
            case "Write a script before saving it.": return "اكتب نصًا قبل حفظه.";
            case "Script saved on this phone.": return "تم حفظ النص على هذا الهاتف.";
            case "New script ready.": return "النص الجديد جاهز.";
            case "Bluetooth & hardware remote": return "Bluetooth والتحكم المادي";
            case "Custom Bluetooth remote": return "وحدة Bluetooth مخصصة";
            case "Remote controls": return "عناصر التحكم عن بُعد";
            case "Reset to standard controls": return "استعادة عناصر التحكم القياسية";
            case "Set button": return "تعيين زر";
            case "Listening — press remote button": return "في وضع الاستماع — اضغط زر التحكم";
            case "Scan for custom remote": return "البحث عن وحدة تحكم مخصصة";
            case "Found remotes": return "وحدات التحكم الموجودة";
            case "Ready to scan.": return "جاهز للبحث.";
            case "Pause": return "إيقاف مؤقت";
            case "Replay": return "إعادة";
            case "Start": return "بدء";
            case "Top": return "البداية";
            case "Starts in": return "يبدأ خلال";
            case "Drag prompt  •  Pinch to resize": return "اسحب الملقّن  •  استخدم إصبعين لتغيير الحجم";
            case "No script yet. Open Prompt Overlay and paste your words.": return "لا يوجد نص بعد. افتح Prompt Overlay والصق كلماتك.";
            case "No script yet. Return and paste your words.": return "لا يوجد نص بعد. ارجع والصق كلماتك.";
            case "Mirror full-screen  •  Script is reversed  •  Hardware remote ready": return "مرآة بملء الشاشة  •  النص معكوس  •  التحكم المادي جاهز";
            case "Off": return "إيقاف";
            case "slow": return "بطيء";
            case "medium": return "متوسط";
            case "fast": return "سريع";
            case "Test build · unlimited prompt time · no purchase flow": return "إصدار تجريبي · وقت ملقّن غير محدود · بلا شراء";
            default: return source;
        }
    }
}
