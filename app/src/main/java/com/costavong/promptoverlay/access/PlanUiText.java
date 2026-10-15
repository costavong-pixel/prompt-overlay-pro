package com.costavong.promptoverlay.access;

import android.content.Context;
import com.costavong.promptoverlay.AppLanguage;

/** Localized labels for the 90-day trial + Play plan chooser. */
public final class PlanUiText {
    private static final String[][] WORDS={
        {"Back","Atrás","Retour","رجوع"},
        {"Check Play purchases (QA only)","Comprobar compras de Play (solo prueba)","Vérifier les achats Play (test uniquement)","التحقق من مشتريات Play (للاختبار فقط)"},
        {"No purchases have been checked. Real purchase restoration is not enabled.","Aún no se han comprobado compras. La restauración real no está habilitada.","Aucun achat vérifié. La restauration des achats n’est pas activée.","لم يتم التحقق من المشتريات. استعادة المشتريات الحقيقية غير مفعّلة."},
        {"Checking Google Play purchases for this test app…","Comprobando compras de Google Play para esta app de prueba…","Vérification des achats Google Play de cette application de test…","جارٍ التحقق من مشتريات Google Play لهذا التطبيق التجريبي…"},
        {"Google Play purchases are unavailable for this QA package.","Las compras de Google Play no están disponibles para este paquete de prueba.","Les achats Google Play ne sont pas disponibles pour cette version de test.","مشتريات Google Play غير متاحة لحزمة الاختبار هذه."},
        {"Google Play reported unverified purchases: ","Google Play notificó compras sin verificar: ","Google Play indique des achats non vérifiés : ","أبلغ Google Play عن مشتريات غير موثّقة: "},
        {"purchased, ","compradas، ","achetés, ","تم شراؤها، "},
        {"pending. No paid access was unlocked.","pendientes. No se ha desbloqueado ningún acceso de pago.","en attente. Aucun accès payant n’a été débloqué.","قيد الانتظار. لم يتم فتح أي وصول مدفوع."},
        {"No Play purchases found for this test package. No paid access was unlocked.","No se encontraron compras de Play para este paquete de prueba. No se desbloqueó el acceso de pago.","Aucun achat Play trouvé pour cette version de test. Aucun accès payant n’a été débloqué.","لم يتم العثور على مشتريات Play لحزمة الاختبار هذه. لم يتم فتح أي وصول مدفوع."},
        {"Your access & plans","Tu acceso y planes","Votre accès et vos offres","وصولك وخططك"},
        {"90 DAYS FREE","90 DÍAS GRATIS","90 JOURS GRATUITS","٩٠ يومًا مجانًا"},
        {"Try every current feature for 90 days — teleprompter, video editor, captions and export.","Prueba todas las funciones actuales durante 90 días: teleprompter, editor de vídeo, subtítulos y exportación.","Essayez toutes les fonctionnalités actuelles pendant 90 jours : téléprompteur, montage vidéo, sous-titres et exportation.","جرّب جميع الميزات الحالية لمدة ٩٠ يومًا: الملقّن وتحرير الفيديو والترجمة النصية والتصدير."},
        {"NO CARD · NO AUTOMATIC CHARGE","SIN TARJETA · SIN COBROS AUTOMÁTICOS","SANS CARTE · AUCUN PRÉLÈVEMENT AUTOMATIQUE","دون بطاقة · دون رسوم تلقائية"},
        {"The 90 days start after your first verified Google sign-in, not when you install.","Los 90 días empiezan tras iniciar sesión con Google por primera vez, no al instalar.","Les 90 jours commencent après votre première connexion Google vérifiée, pas à l’installation.","تبدأ التسعون يومًا بعد أول تسجيل دخول موثّق بحساب Google، وليس عند التثبيت."},
        {"Activate trial / Check status","Activar prueba / Ver estado","Activer l’essai / Vérifier l’état","تفعيل التجربة / التحقق من الحالة"},
        {"Account service not connected yet. Trial activation is unavailable in this test build.","El servicio de cuentas aún no está conectado. No puedes activar la prueba en esta versión.","Le service de compte n’est pas encore connecté. L’essai ne peut pas être activé dans cette version.","خدمة الحساب غير متصلة بعد. تفعيل التجربة غير متاح في هذا الإصدار التجريبي."},
        {"Sign in with Google to activate your trial.","Inicia sesión con Google para activar tu prueba.","Connectez-vous avec Google pour activer votre essai.","سجّل الدخول بحساب Google لتفعيل تجربتك."},
        {"Checking account status…","Comprobando el estado de la cuenta…","Vérification du compte…","جارٍ التحقق من حالة الحساب…"},
        {"Account status unavailable. Try again when the service is connected.","El estado no está disponible. Inténtalo cuando el servicio esté conectado.","Statut indisponible. Réessayez lorsque le service sera connecté.","حالة الحساب غير متاحة. أعد المحاولة بعد الاتصال بالخدمة."},
        {"Your trial is active.","Tu prueba está activa.","Votre essai est actif.","تجربتك نشطة."},
        {"Trial completed. Your projects remain on your phone.","Prueba finalizada. Tus proyectos siguen en tu teléfono.","Essai terminé. Vos projets restent sur votre téléphone.","انتهت التجربة. تظل مشاريعك محفوظة على هاتفك."},
        {"Basic Lifetime is active.","Basic de por vida está activo.","Basic à vie est actif.","خطة Basic مدى الحياة نشطة."},
        {"Pro Monthly is active.","Pro mensual está activo.","Pro mensuel est actif.","خطة Pro الشهرية نشطة."},
        {"Pro Lifetime is active.","Pro de por vida está activo.","Pro à vie est actif.","خطة Pro مدى الحياة نشطة."},
        {"AFTER YOUR FREE TRIAL","DESPUÉS DE TU PRUEBA GRATIS","APRÈS VOTRE ESSAI GRATUIT","بعد التجربة المجانية"},
        {"Choose a plan only if you want to continue. Nothing is charged when the free trial ends.","Elige un plan solo si quieres continuar. No se cobra nada al terminar la prueba.","Choisissez une offre seulement pour continuer. Aucun frais à la fin de l’essai gratuit.","اختر خطة فقط إذا رغبت في الاستمرار. لن يتم تحصيل أي مبلغ عند انتهاء التجربة."},
        {"Basic Lifetime","Basic de por vida","Basic à vie","Basic مدى الحياة"},
        {"Pro Monthly","Pro mensual","Pro mensuel","Pro شهري"},
        {"Pro Lifetime","Pro de por vida","Pro à vie","Pro مدى الحياة"},
        {"one-time","pago único","paiement unique","دفعة واحدة"},
        {"per month · renews after purchase","al mes · se renueva tras la compra","par mois · renouvellement après l’achat","شهريًا · تتجدد بعد الشراء"},
        {"All Basic teleprompter features forever.","Todas las funciones básicas del teleprompter para siempre.","Toutes les fonctions de base du téléprompteur à vie.","جميع الميزات الأساسية للملقّن إلى الأبد."},
        {"All current Pro tools while subscribed.","Todas las herramientas Pro actuales mientras estés suscrito.","Tous les outils Pro actuels tant que l’abonnement est actif.","جميع أدوات Pro الحالية طوال فترة الاشتراك."},
        {"All current non-AI Pro tools permanently.","Todas las herramientas Pro actuales sin IA para siempre.","Tous les outils Pro actuels sans IA à vie.","جميع أدوات Pro الحالية غير المعتمدة على الذكاء الاصطناعي بشكل دائم."},
        {"See included features","Ver funciones incluidas","Voir les fonctions incluses","عرض الميزات المشمولة"},
        {"Basic includes scripts, formatting, imports, overlay controls and local remote features.","Basic incluye guiones, formato, importación, controles superpuestos y mando local.","Basic comprend les scripts, la mise en forme, l’importation, le contrôle superposé et la télécommande locale.","تشمل Basic النصوص والتنسيق والاستيراد وعناصر التحكم العائمة والتحكم عن بُعد المحلي."},
        {"Pro includes Basic plus the local four-track editor, trim/split, captions, graphics, music and up to 1080p export.","Pro incluye Basic más el editor local de cuatro pistas, recorte/división, subtítulos, gráficos, música y exportación hasta 1080p.","Pro comprend Basic ainsi que le montage local à quatre pistes, le découpage, les sous-titres, les éléments graphiques, la musique et l’export jusqu’à 1080p.","تشمل Pro ميزات Basic إضافة إلى محرر محلي بأربعة مسارات وقص وتقسيم وترجمة ورسومات وموسيقى وتصدير حتى 1080p."},
        {"Lifetime excludes future metered cloud AI services and credits.","La licencia de por vida no incluye futuros servicios de IA en la nube ni créditos.","L’offre à vie exclut les futurs services d’IA cloud facturés à l’usage et les crédits.","الخطة مدى الحياة لا تشمل خدمات الذكاء الاصطناعي السحابية المستقبلية أو أرصدتها."},
        {"Plans are not for sale in this test build.","Los planes aún no están a la venta en esta versión de prueba.","Les offres ne sont pas en vente dans cette version de test.","الخطط ليست معروضة للبيع في هذا الإصدار التجريبي."},
        {"US target prices shown until live Google Play offers become available. Checkout is disabled.","Precios previstos en USD hasta que Google Play muestre ofertas reales. El pago está desactivado.","Prix indicatifs en USD jusqu’à la disponibilité des offres Google Play. Paiement désactivé.","الأسعار المعروضة تقديرية بالدولار حتى توفر عروض Google Play الفعلية. الدفع معطّل."},
        {"Price from Google Play","Precio de Google Play","Prix Google Play","السعر من Google Play"},
        {"Google Play pricing is not available for this test package.","Los precios de Google Play no están disponibles para esta versión de prueba.","Les tarifs Google Play ne sont pas disponibles pour cette version de test.","أسعار Google Play غير متاحة لحزمة الاختبار هذه."},
        {"View feature details","Ver detalles de funciones","Afficher les détails des fonctions","عرض تفاصيل الميزات"},
        {"Win a FREE Lifetime License from Us","Gana una licencia GRATIS de por vida","Gagnez une licence à vie GRATUITE","اربح ترخيصًا مجانيًا مدى الحياة"},
        {"We will choose 500 users based on specific, actionable private feedback. Photos, videos and links are welcome.","Elegiremos a 500 usuarios según comentarios privados específicos y útiles. Puedes añadir fotos, vídeos y enlaces.","Nous choisirons 500 utilisateurs sur la base de retours privés précis et utiles. Photos, vidéos et liens bienvenus.","سنختار ٥٠٠ مستخدم بناءً على ملاحظات خاصة محددة ومفيدة. يمكن إرفاق الصور والفيديوهات والروابط."},
        {"Google Play reviews are independent and do not affect selection.","Las reseñas de Google Play son independientes y no afectan a la selección.","Les avis Google Play sont indépendants et n’influencent pas la sélection.","مراجعات Google Play مستقلة ولا تؤثر في الاختيار."},
        {"Development preview · no payments","Vista previa de desarrollo · sin pagos","Aperçu de développement · sans paiement","معاينة تطوير · دون مدفوعات"},
        {"Close","Cerrar","Fermer","إغلاق"},
        {"Learn more","Más información","En savoir plus","اعرف المزيد"},
        {"days left","días restantes","jours restants","أيام متبقية"},
    };
    // The purchase-check button never grants ownership; responses require backend verification.
    private PlanUiText(){}
    public static String t(Context context,String english){
        if(english==null)return "";
        int language;
        switch(AppLanguage.current(context)){
            case "es":language=1;break;
            case "fr":language=2;break;
            case "ar":language=3;break;
            default:return english;
        }
        for(String[] translation:WORDS){
            if(english.equals(translation[0]))return translation[language];
        }
        return english;
    }
}
