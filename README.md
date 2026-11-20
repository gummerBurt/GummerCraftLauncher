<div align="center">

![GummerCraft - З України, з любов'ю!](launcher/src/main/resources/com/skcraft/launcher/ui/frame/logo.png)

</div>

# GummerCraft Launcher

Лаунчер Minecraft для серверів [GummerCraft](https://gummercraft.fun): встановлює та оновлює збірки модів і запускає гру з акаунтом Microsoft.

**[Завантажити лаунчер для Windows](https://launch.gummercraft.fun/install/GummerCraft.exe)** (один файл, Java вже всередині) · [без вбудованої Java](https://launch.gummercraft.fun/install/exe) · [jar для Linux і macOS](https://launch.gummercraft.fun/install/jar) · [сайт GummerCraft](https://gummercraft.fun)

*Minecraft launcher for the GummerCraft servers. A fork of [SKCraft Launcher](https://github.com/SKCraft/Launcher), licensed under LGPL v3. English summary at the bottom.*

## StalDayZ

<div align="center">

![StalDayZ - Прип'ять](docs/images/staldayz.jpg)

</div>

**[StalDayZ](https://staldayz.gummercraft.fun/)** - наш сервер за мотивами S.T.A.L.K.E.R. і DayZ у Minecraft: Зона відчуження, лутінг, аномалії й артефакти, викиди, радіація, мутанти, квести, торгівля та бази гравців.

Зайти на StalDayZ можна прямо з цього лаунчера: обираєш у списку **StalDayZ**, натискаєш «Запуск», а всі потрібні моди лаунчер завантажить і оновлюватиме сам. Крім StalDayZ, у лаунчері є збірки GummerCraft для різних версій Minecraft і чистий Minecraft від Mojang (розділ Vanilla).

## Що нового в 1.36.4

- **Усі звернення в підтримку в одному чаті:** у вікні «Підтримка» видно всі свої звернення та відповіді адміністрації однією стрічкою, тож відповідь на старе звернення більше не загубиться.
- **Відповідь підтримки видно одразу:** коли адміністрація відповіла, лаунчер показує вікно з текстом відповіді. Якщо після вильоту відповідь ще не переглянута, спершу покаже її, а вже потім нове звернення.
- **Кирилиця в шляху до гри:** якщо гра не запускається, бо Windows не передає Java літери в шляху (наприклад «Документи»), у вікні вильоту є кнопка «Перевстановити до нової теки».
- Java, вибрана в налаштуваннях вручну, не заважає запуску, якщо її видалили або вона застара для цієї версії гри: тоді на цей запуск береться Java лаунчера.

## Що нового в 1.36.3

- **Імпорт збірок з інших лаунчерів:** рядок «Імпорт збірки» під Modrinth у списку збірок та вкладка «Імпорт/Експорт» у вікні «Збірка». Лаунчер сам знаходить збірки Prism Launcher, PolyMC, ATLauncher, CurseForge, Modrinth App (стара версія), TLauncher, SKlauncher та офіційного лаунчера Minecraft, визначає версію гри та завантажувач і переносить моди, налаштування, ресурспаки та шейдери в нову збірку, а світи за бажанням. Теку MultiMC чи будь-якого іншого лаунчера можна вибрати вручну. Нового Modrinth App поки немає: він зберігає збірки у своїй базі.
- **Вибір відеокарти** (вікно «Збірка», «Загальне», лише на ПК з двома відеокартами): «Як вирішить система», інтегрована або дискретна, з назвами відеокарт. Що при цьому змінюється на комп'ютері, чесно описано в розділі «Що лаунчер робить на комп'ютері».
- **Видалення одним кліком:** у налаштуваннях, «Додатково», дві червоні кнопки «Видалити всі збірки» та «Видалити лаунчер та всі збірки». Лаунчер покаже, скільки місця звільниться, попросить підтвердження та видалить лише свої файли.
- **Оновлення Java** (налаштування, «Java та пам'ять»): видно, яка Java стоїть у лаунчера та у версій гри і чи є новіша. Кнопки «Оновити» та «Відкатити», а автооновлення Java можна вимкнути.
- **Помічник знає більше.** Перед запуском попереджає про мод не під ту версію Minecraft та про пари модів, що не працюють разом. Після вильоту розпізнає зламаний конфіг мода, неправильні параметри Java, пошкоджений світ та старий Forge з кирилицею в шляху. Мод, що почав вилітати після оновлення, можна повернути на попередню версію однією кнопкою.
- **Місце на диску** перевіряється перед завантаженням: якщо не вистачає, лаунчер скаже, скільки треба звільнити і на якому диску.
- **Антивірус заблокував файл:** лаунчер так і скаже та підкаже, що робити, замість незрозумілої помилки.
- **Спрощений режим на Mac та Linux:** без розмиття та анімацій, тож лаунчер не гальмує. На Linux звичайна рамка вікна. Вигляди та всі функції ті самі.
- **Швидше:** вкладки вікна «Збірка» перемикаються без затупів навіть у великих збірках, а головне вікно відкривається швидше.
- Порожній список «Встановлені» тепер пропонує перейти до всіх збірок або імпортувати збірку з іншого лаунчера.

## Що нового в 1.36

- Кнопка **«Збірка»** біля «Грати»: назва збірки, копії, моди, ресурспаки, шейдери, світи і скріншоти в одному вікні.
- **Копії збірок:** ще одна збірка тієї самої версії зі своїми модами і світами. В меню збірки (права кнопка) є кнопка «Дублювати»: копія в один клік.
- **Своя назва, іконка та фон** у будь-якої встановленої збірки, крім GummerCraft та StalDayZ: версії Vanilla, завантажувачі, модпаки Modrinth та їх копії. Свою іконку видно в списку «Встановлені», фон у головному вікні. Копія з'являється у виборі версії свого розділу, одразу під версією, з якої вона зроблена.
- **Моди вмикаються і вимикаються** галочкою. Лаунчер попереджає, якщо від мода залежать інші.
- **Ресурспаки, шейдери і моди з Modrinth** прямо у вікні «Збірка», у вкладках «Знайти»: пошук, популярне під твою версію гри, встановлення в один клік. Моди можна додавати і в збірки GummerCraft (окрім StalDayZ). Біля кожного мода видно, чи він клієнтський, серверний чи клієнт+сервер. У пошуку модів можна вибрати «Клієнт» чи «Сервер», щоб бачити лише відповідні моди. Потрібні моду залежності (наприклад Fabric API) лаунчер поставить сам і не поставить вдруге те, що вже є. Кожен файл звіряється з хешем Modrinth.
- **«Встановити без запуску»:** збірку можна поставити заздалегідь, не заходячи в гру.
- **Оновлення модів:** лаунчер сам бачить нові версії модів, які ти поставив, та оновлює їх по одному або кнопкою «Оновити все». Залежності мода ставляться самі, а якщо чогось бракує чи щось несумісне, лаунчер про це повідомить.
- **Модпаки з Modrinth** прямо в лаунчері, вкладка «Модпаки»: модпак з'являється у виборі версії свого завантажувача (наприклад Forge 1.16.5) під своєю назвою.
- **Модпаки одним кліком:** рядок «Modrinth» у списку збірок відкриває всі модпаки Modrinth з пошуком та фільтрами; вибираєш модпак і його версію, і лаунчер сам завантажить модпак, гру та завантажувач. Видно, скільки мегабайтів уже скачано.
- **«Усі / Встановлені»** над списком збірок: «Встановлені» показують кожну збірку на диску окремим рядком зі своєю іконкою. У виборі версії встановлене стоїть угорі.
- **Снапшоти Minecraft у Vanilla:** снапшоти, пре-релізи та кандидати наступної версії (зараз 26.4 Snapshot 1-10) стоять одразу під останнім релізом. Коли вийде реліз, його снапшоти зі списку зникнуть, але встановлені снапшоти залишаться та запускатимуться. У завантажувачах снапшотів немає.
- **«Поділитися збіркою»:** збережи збірку у файл `.mrpack` або `.zip` (усі моди всередині) та скинь її другу, і він зможе відкрити її у своєму лаунчері, котрий підтримує імпорт `.mrpack`.
- **Вікно на весь екран** - головне вікно можна розгорнути та розтягнути.
- **Зрозумілий виліт:** якщо гру вбив мод, лаунчер скаже, який саме, та запропонує його вимкнути; якщо одному моду бракує іншого, лаунчер повідомить, якого. Якщо мод вимагає новішу версію іншого мода, лаунчер перед запуском сам поставить її з Modrinth.
- **Мова прапорцями на головній:** плашки UA і EN у головному вікні. Мова міняється одразу, без перезапуску лаунчера.
- **Перевірка перед запуском:** якщо файлів гри бракує чи вони пошкоджені, лаунчер докачає їх сам перед стартом.
- **Помічник при вильоті:** після вильоту гри вікно пропонує докачати файли, оновити мод, що вбив гру, або вимкнути його.
- **Повернути до початкового стану:** збірку з Modrinth можна повернути до стану після встановлення модпака, а копію до стану на момент копіювання. Світи, скріншоти та налаштування залишаються як були.
- **Оновлення модпаків Modrinth** зі списком змін від авторів та можливістю повернутись на попередню версію.
- **Список змін модів:** перед оновленням модів видно, що написали автори про нові версії.
- **Пресети ФПС** (вікно «Збірка», «Загальне»): «Слабкий ПК», «Збалансовано», «Красиво». Лаунчер радить пресет під пам'ять та відеокарту ПК, а кнопка «Повернути налаштування гри» повертає графіку як до першого пресета, не чіпаючи налаштування клавіш та звуку.
- Лаунчер пам'ятає хеші модів між запусками, тож вікно «Збірка» та перевірка оновлень відкриваються швидше.
- Анонімна статистика: скільки своїх модів у збірці (лише число) та що завантажено з пошуку (лише тип: мод, ресурспак, шейдер, модпак). Вимикається тим самим `off` у `stats-id.txt`. Подробиці в [SECURITY.md](SECURITY.md).

## Що нового в 1.35

- Три вигляди лаунчера на вибір: **Liquid Glass** (за замовчуванням), **Класика** та **Бібліотека**. Перемикаються прямо в головному вікні (блок «Вигляд» унизу) або в налаштуваннях. У кожного вигляду свої вікна налаштувань, підтримки, акаунта та свої сповіщення.
- Дизайну, котрий подобається, можна поставити лайк (один з IP на вигляд), а кнопка «Фідбек по дизайну» відкриває розмову з нами.
- Налаштування лаунчера по-новому: розділи «Загальні», «Java та пам'ять», «Додатково», з прокруткою, тож усе видно і на малому екрані.
- Відповідь підтримки видно одразу: число непрочитаних на «Підтримці» оновлюється щохвилини, а не лише при старті лаунчера.
- Якщо лаунчер не зміг запуститись, вікно «Лаунчер не запустився» пояснює, що робити, і дає скопіювати деталі для підтримки.
- Перша установка гри швидша та надійніша: файли Minecraft (звуки, текстури, бібліотеки) качаються кількома великими архівами з нашого CDN, а не тисячами дрібних файлів від Mojang. Обірвався інтернет чи зникло світло? Наступного разу лаунчер докачає архів з того ж місця. Кожен файл перевіряється, тимчасові файли прибираються. Під час оновлення, як і раніше, завантажуються лише зміни.
- «Музей» у розділі Vanilla: версії, старіші за 1.8, аж до найпершої rd-132211, а також Classic, Infdev, Alpha та Beta. Запускаються, але звуків і скінів у найстаріших версій немає: сервери Mojang для них давно вимкнені.
- Лог лаунчера тепер зберігається у файлі `logs/launcher.log` (+ два попередні запуски), тож проблему можна розібрати навіть після перезапуску. У звернення в підтримку з галочкою «Додати лог» він додається автоматично.
- Анонімна подія «завантаження збірки»: скільки йшла установка та з якого джерела гальмувало. Без ніка, акаунта та назв файлів. Вимикається тим самим `off` у `stats-id.txt`. Подробиці в [SECURITY.md](SECURITY.md).

## Що нового в 1.34.1

Виправлення: лаунчер перевіряє кожен завантажений файл і перекачує зіпсований, встановлення модпака на 32-бітній Java більше не падає з нестачею пам'яті.

## Що нового в 1.34

**Акаунт на ГаммерКрафті (кабінет)**
- Голова в кабінеті та на кнопці акаунта показує твій скін з того сервера, де він є: спершу GummerCraft, потім StalDayZ. Раніше скін брався лише зі StalDayZ, тому в багатьох була голова Стіва.
- Якщо скін ще не завантажено, видно стандартний скін з підказкою, а не «?».
- Якщо ти вибрав скін, але не завантажив його на сервер, лаунчер спитає «Завантажити зараз?», коли закриваєш вікно або повертаєшся до кабінету. Напис «Ще не завантажено: <файл>» більше не ховається за довгою назвою файлу.
- Після завантаження лаунчер перевіряє, що сервер справді зберіг скін, і повідомляє, якщо ні.
- Привілеї пишуться як VIP1, VIP2, VIP3. Пояснено, чому в давніх гравців немає старої статистики.

**Головне вікно**
- Онлайн серверів оновлюється частіше: раз на 30 с, коли вікно активне, і раз на 90 с у фоні. Коли повертаєшся у вікно, цифра оновлюється одразу. Згорнутий лаунчер сервери не опитує. Картки новин більше не блимають при оновленні.
- У вікні «Обери акаунт для гри» нагорі той акаунт, яким грав останнім, і він уже вибраний.
- Вікно «Гра закрилась з помилкою» тепер з'являється над лаунчером, а не під ним.

## Що нового в 1.33.1

- Тексти лаунчера українською відредаговано: звертання на «ти», однакові терміни, виправлено описки.
- У діалозі перевстановлення Vanilla і завантажувачів знову видно питання «Бажаєш продовжити?».
- Анонімна подія «гру запущено» (яку збірку запущено, без ніка, акаунта і даних про комп'ютер), вимикається словом `off` у `stats-id.txt`. Подробиці в [SECURITY.md](SECURITY.md).

## Що нового в 1.33

**Завантажувачі модів**
- Нові розділи **Fabric**, **Quilt**, **Forge** та **NeoForge**. Це чистий Minecraft з завантажувачем, відповідні моди можна завантажити в теку mods (меню правою кнопкою миші, «Відкрити теку модів»). Версія Minecraft обирається тим самим списком біля «Запуск».
- Fabric від 1.14, Quilt для тих самих версій, що підтримує Quilt, Forge від 1.7.10 (включно зі старими 1.7.10-1.12.2), NeoForge від 1.20.2. Нові версії з'являються автоматично.
- Версія завантажувача ставиться найновіша стабільна (у Forge рекомендована) і далі сама не змінюється. Змінити можна в меню правою кнопкою, «Версія ...».
- Forge та NeoForge лаунчер готує сам, так само як офіційний установник: установник завантажується з maven Forge чи NeoForge з перевіркою sha1, його кроки виконуються всередині лаунчера. Тому перша установка версії може тривати до хвилини.

**Аккаунт на ГаммерКрафті (необов'язково)**
- Вхід ніком та паролем від сервера, можлива реєстрація ніку заздалегідь. «Забув пароль?» відкриває форму підтримки з готовим текстом.
- Скіни для StalDayZ та GummerCraft окремо, прямо з лаунчера (PNG 64x64 або 64x32, класичні чи тонкі руки, прев'ю).
- Особистий кабінет зі статистикою гравця: коли створено акаунт, перший та останній вхід, скільки награно та на яких серверах, донат з датою закінчення.
- Пароль лаунчер не зберігає, зберігається лише токен терміном на 30 днів. Зміна пароля робить токен недійсним.
- Без входу все працює як раніше - локальний акаунт з будь-яким ніком.

**Підтримка**
- Після відключення від сервера в грі з'являється кнопка «Надіслати звіт у техпідтримку». Лаунчер відкриває форму з сервером, часом та причиною відключення (потрібні нові версії модів GummerCraft та StalDayZ).
- Форма підтримки та вікна помилок працюють і в гравців зі старим exe.
- Зрозуміліші підготовані наперед тексти, коли сервер підтримки відповів помилкою.

**Інше**
- Linux: гра більше не вилітає через нативний транспорт Netty (epoll), розбір вильотів упізнає проблеми libGL та GLX.
- Старий exe працює як і раніше та сам оновлюється до 1.33.

## Що нового в 1.32

**Java більше не потрібна**
- Новим гравцям один файл `GummerCraft.exe`, у якому вже є Java (Eclipse Temurin: 17 на Windows 10 і 11, 8 на Windows 7 і 8.1). Перший запуск кілька секунд розпаковує його в `%LOCALAPPDATA%\GummerCraft\app` (лише смужка «Перший запуск», без питань і прав адміністратора), далі exe одразу відкриває лаунчер. Ярликів і запису в «Програми та компоненти» немає, щоб прибрати, досить видалити папку.
- Для Windows 10 і 11 є ще портативний zip.
- Старий exe працює як і раніше і сам оновлюється до 1.32.

**Список збірок**
- Три розділи: StalDayZ, GummerCraft (збірки для різних версій Minecraft) і Vanilla (чистий Minecraft від Mojang, усі релізи від 1.8, нові з'являються самі).
- Версія Minecraft вибирається великим списком біля кнопки «Запуск». У ньому видно стан кожної версії: зелена крапка означає встановлено, жовта означає є оновлення, порожнє коло означає не встановлено, найновіша позначена окремо.
- Онлайн сервера біля назви збірки і біля кнопки «Запуск». Галочка «Одразу на сервер» запускає гру з входом на сервер збірки.
- Оформлення вікна для Vanilla: свій фон і логотип Minecraft.
- Лаунчер пам'ятає останню вибрану збірку і більше не забуває її, коли список ще вантажиться.

**Гра без інтернету**
- Встановлені збірки запускаються без інтернету: лаунчер бере збережену копію списку збірок, а замість входу в Microsoft пропонує грати зі збереженим профілем (той самий нік і ті самі світи; одиночна гра і сервери без перевірки акаунта).
- Якщо оновлення збірки не вдалося через мережу, лаунчер пропонує запустити встановлену версію.
- Без інтернету немає вікон з помилками про перевірку оновлень.

**Коли щось пішло не так**
- Зрозумілі вікна помилок: що сталося людською мовою, що зробити, технічні деталі окремо і кнопка «Написати в підтримку».
- Розбір вильотів гри: брак пам'яті, не та версія Java, драйвер відеокарти, конфлікт або дубль модів, пошкоджені файли, мало місця на диску. Лаунчер підказує, що робити.
- Підтримка прямо з лаунчера: звернення стає чатом з командою GummerCraft, лог і дані системи додаються самі.
- У вікні завантаження видно розмір, швидкість і скільки лишилось.

**Інше**
- Вікно лаунчера підлаштовується під розмір екрана.
- Якщо у збірки є рекомендована пам'ять, лаунчер піднімає ліміт до неї, лишаючи системі щонайменше 2 ГБ.
- Нова адреса CDN `cdn.gummercraft.fun`.
- Linux: вимкнено нативний транспорт Netty (epoll), через який гра вилітала з `DecoderException`; розбір вильотів упізнає проблеми libGL і GLX.
- Анонімний випадковий ID установки, щоб рахувати гравців, а не IP-адреси. Він ні з чого не виводиться і вимикається словом `off` у `stats-id.txt` (подробиці в [SECURITY.md](SECURITY.md)).
- З 1.33.1: анонімна подія «гру запущено». Лаунчер повідомляє наш CDN, яку збірку запущено (назва нашої збірки або тип і версія Minecraft для Vanilla, Fabric, Quilt, Forge, NeoForge), щоб ми бачили, чим користуються гравці. Ніка, акаунта, модів і даних про комп'ютер там немає. Те саме `off` вимикає і її.
- Непримітне посилання «Код лаунчера на GitHub» у вікні.

## Що нового в 1.31

- Службова DLL бібліотеки інтерфейсу FlatLaf тепер справді не розпаковується в тимчасову папку Windows (виправлення з 1.29 спрацьовувало не завжди: FlatLaf вантажив DLL раніше, ніж отримував шлях до папки лаунчера).
- Інсталятор визначає папку "Документи" напряму, без вікна вибору файлів Java, яке побічно перебирало ярлики на робочому столі.
- Якщо на комп'ютері немає Java, інсталятор качає її прямо з сайту Oracle, без проміжної переадресації через наш сайт.
- Скрипт `tools/verify-cdn.py`: будь-хто може однією командою перевірити, що офіційний лаунчер на CDN зібраний із цього коду (дивись «Перевірка офіційної збірки»).
- Прибрано невикористаний клас `PastebinPoster` з оригінального SKCraft (логи вивантажуються лише на наш сервер логів, кнопкою в консолі).

## Що нового в 1.30

- Фон і логотип вікна лаунчера міняються разом із вибраною збіркою, з плавним переходом: у StalDayZ свій фон і логотип S.T.A.L.DAYZ, у решти - звичні.
- Кнопки «Вікі та FAQ» і «Новини та чат» для StalDayZ ведуть на вікі й Telegram-канал StalDayZ.
- Лаунчер пам'ятає останню вибрану збірку й одразу відкривається з нею та її оформленням.
- Оновлення лаунчера ставляться самі, без питань: при запуску лаунчер завантажує нову версію (з перевіркою SHA-256) і перезапускається.

## Що нового в 1.29

Разом із відкриттям коду ми провели аудит лаунчера і виправили знайдене:

- **Увімкнено повну перевірку HTTPS-сертифікатів.** Раніше її було вимкнено як обхідне рішення для старих Windows і Java, яким бракувало нових кореневих сертифікатів. Через це в незахищених мережах (наприклад, публічний Wi-Fi) з'єднання лаунчера теоретично можна було перехопити. Тепер кожне з'єднання перевіряється повністю, а для старих Java лаунчер додає кілька офіційних кореневих сертифікатів до системних (див. `TrustedTls`).
- **Лаунчер і його оновлення завантажуються тільки по HTTPS і звіряються з контрольною сумою SHA-256**, опублікованою в `release.json`.
- **Exe одразу запускає найновішу версію:** при кожному старті він звіряє встановлену версію з опублікованою і, якщо вийшла новіша, ставить її до запуску. Без інтернету запускається встановлена.
- **Лог консолі відправляється по HTTPS** (лише після натискання кнопки «Завантажити лог»).
- **Бібліотека інтерфейсу FlatLaf більше не розпаковує DLL у тимчасову папку Windows** - файл лежить у папці лаунчера.
- **Прибрано невикористаний код телеметрії та ідентифікатор `uuid` з налаштувань.** Лаунчер не збирає і не відправляє даних про комп'ютер. (З 1.32 є анонімний випадковий ID установки для підрахунку гравців, з 1.33.1 анонімна подія «гру запущено» з назвою збірки, див. [SECURITY.md](SECURITY.md).)
- **Офіційний ID застосунку Microsoft винесено з коду** в налаштування збірки.

Що лаунчер робить і з якими серверами з'єднується - у [SECURITY.md](SECURITY.md).

## Походження та ліцензія

Проєкт є форком **SKCraft Launcher** (Albert Pham / sk89q та контриб'ютори) і розповсюджується на тих самих умовах - **GNU LGPL v3**, повний текст у [LICENSE.txt](LICENSE.txt). Історія оригінального проєкту - в [репозиторії SKCraft](https://github.com/SKCraft/Launcher).

Зміни команди GummerCraft (2021–2025): новий інтерфейс на FlatLaf (`launcher-flat`), українська локалізація, власні маніфести збірок і Java-рантаймів, сповіщення, офлайн-скіни, перевірка оновлень. Файли, додані командою, лежать у пакетах `com.gummer.*` і мають заголовок `Copyright (C) GummerCraft`; змінені файли SKCraft мають обидва копірайти.

## Що лаунчер робить на комп'ютері

- Усі файли - **в одній папці**: `Документи\GummerCraft` (Windows) або `~/.gummercraft` / `$XDG_DATA_HOME/GummerCraft` (Linux, macOS). Шлях зберігається в `bootstrap.yml` і його можна змінити. Портативний режим - файл `portable.txt` поруч із лаунчером.
- Вміст папки: `launcher/` (jar лаунчера), `runtime/` (портативна Java - збірки Eclipse Temurin), `assets/`, `libraries/`, `versions/` (файли Minecraft), `instances/` (збірки), `config.json` (налаштування), `accounts.dat` (збережені акаунти), з 1.32 `support.json` (звернення в підтримку з лаунчера), `launcher-ui.json` (вкладка, остання збірка, «Одразу на сервер»), `news-cache.json` і `vanilla_versions.json` (копії новин і списку версій Mojang), `stats-id.txt` (анонімний випадковий ID установки для підрахунку гравців, нічого про вас не містить; `off` у файлі вимикає, деталі в [SECURITY.md](SECURITY.md)). Ванільні версії з вкладки Vanilla ставляться в `instances/vanilla-<версія>`. З 1.33 розділи завантажувачів ставляться в `instances/fabric-<версія>`, `quilt-<версія>`, `forge-<версія>`, `neoforge-<версія>` (вибрана версія завантажувача у файлі `fabric-loader.txt` і подібних у теці паку), списки версій зберігаються в `fabric_games.json`, `fabric_loaders.json`, `quilt_games.json`, `quilt_loaders.json`, `forge_versions.xml`, `forge_promotions.json`, `neoforge_versions.xml`, офіційні установники Forge і NeoForge в `loaders/`. Токен і нік аккаунта на ГаммерКрафті (якщо гравець увійшов) у `launcher-ui.json`, пароль ніде. З 1.32 може з'явитись `launcher/ui-scale.txt`: зменшений масштаб інтерфейсу, якщо вікно лаунчера з масштабом Windows не влазить в екран (видалення файлу нічого не ламає).
- З 1.32 для нових гравців є один файл `GummerCraft.exe` з вбудованою Java (Eclipse Temurin JRE 17 на Windows 10+, JRE 8 на Windows 7/8.1). При першому запуску він кілька секунд розпаковує лаунчер і Java в `%LOCALAPPDATA%\GummerCraft\app` (без прав адміністратора), далі одразу відкриває лаунчер. Ярликів, записів у «Програми та компоненти» і змін у реєстрі він не робить; щоб прибрати, досить видалити цю папку або з 1.36.3 натиснути в налаштуваннях лаунчера «Видалити лаунчер та всі збірки». Пункт нижче стосується самого лаунчера.
- Лаунчер **не пише в реєстр**, з одним винятком, який гравець вмикає сам: з 1.36.3 вибір відеокарти для збірки (вікно «Збірка», видно лише на ПК з двома відеокартами: «Як вирішить система», «назва (інтегрована)», «назва (дискретна)», за замовчуванням перше). Якщо гравець вибрав відеокарту, перед запуском гри лаунчер записує одне значення в `HKCU\Software\Microsoft\DirectX\UserGpuPreferences`: назва це шлях до Java, яку поставив сам лаунчер, вміст `GpuPreference=1;` («Енергозбереження», інтегрована) або `GpuPreference=2;` («Висока продуктивність», дискретна). Рівно це записує і сторінка Windows «Параметри графіки», і запис видно там же. Системну Java та вибір, уже зроблений гравцем у Windows, лаунчер не чіпає; повернути «Як вирішить система» або видалити збірки означає прибрати запис. Кожна дія йде в лог лаунчера, а список записаного лежить у `gpu-prefs.txt` у його папці. На Linux для дискретної лаунчер лише додає грі змінні PRIME (`DRI_PRIME` або PRIME offload NVIDIA) на цей запуск, на macOS вибору немає. Крім цього лаунчер лише читає ключі встановленої Java, щоб її знайти, і з 1.36 назви відеокарт (для поради пресета ФПС і для цього вибору; назви нікуди не надсилаються), **не додає себе в автозапуск**, не створює служб, задач планувальника чи ярликів і не змінює файли поза своєю папкою.
- З 1.36.3 у налаштуваннях («Додатково», «Видалення») є «Видалити всі збірки» і «Видалити лаунчер та всі збірки». Друга кнопка прибирає свою папку даних, `%LOCALAPPDATA%\GummerCraft\app` і `bootstrap.yml` у Документах. Видаляється лише те, що створив лаунчер, за списком назв: чужі файли в папці даних лишаються.
- Єдиний процес, який він запускає, - сама гра.

Детально про мережеві з'єднання, акаунти та відомі обмеження - у [SECURITY.md](SECURITY.md).

## Збірка

Потрібен **JDK 8** (збірка з іншою версією Java зупиниться з помилкою).

1. Скопіюйте `gradle.properties.example` у `gradle.properties` (файл у `.gitignore`).
2. Впишіть у нього `microsoftClientId` - ID власного застосунку Azure для входу через Microsoft ([реєстрація застосунку](https://learn.microsoft.com/entra/identity-platform/quickstart-register-app), тип «Personal Microsoft accounts», redirect `http://localhost`; для доступу до API Minecraft Microsoft вимагає окремо подати застосунок на схвалення). Без цього збірка зупиниться з помилкою. Офіційний ID GummerCraft у репозиторій не входить.
3. Якщо `JAVA_HOME` вказує не на JDK 8, пропишіть там же `org.gradle.java.home`.

```bash
./gradlew clean build
```

Або `build.bat` (Windows) / `build.sh` (Linux, macOS).

Готові файли - у `<модуль>/build/libs`. Exe-обгортка бутстрапера (Launch4j):

```bash
./gradlew clean build createAllExecutables
```

Результат - `launcher-bootstrap/build/launch4j/Gummer_Craft_launcher.exe`. Офіційні релізи команда підписує власним сертифікатом; ключі підпису не входять у репозиторій.

## Перевірка офіційної збірки

Офіційний лаунчер на CDN збирається з цього ж коду. Перевірити це може будь-хто:

```bash
./gradlew clean build
python tools/verify-cdn.py
```

Скрипт (лише Python 3, без сторонніх пакетів) бере адресу й SHA-256 поточної версії з `cdn.gummer.cc/launcher/install/release.json`, завантажує офіційний jar, звіряє хеш і порівнює вміст з вашою збіркою файл за файлом. Відрізнятися має лише `launcher.properties`: ID застосунку Microsoft (у репозиторій не входить) і дані збірки (`commit`, `branch`). Офіційний jar містить коміт цього репозиторію, з якого його зібрано; якщо ваша збірка з іншого коміту, скрипт підкаже, який зробити `git checkout`. Хеш jar цілком не збігатиметься ніколи - у zip записані дати файлів, тому порівнюється вміст.

## Модулі

| Модуль | Призначення |
|---|---|
| `launcher-bootstrap` | бутстрапер (exe/jar): знаходить робочу папку, завантажує й оновлює jar лаунчера |
| `launcher` | ядро: акаунти, встановлення збірок, запуск гри |
| `launcher-flat` | інтерфейс GummerCraft (FlatLaf) |
| `launcher-shared` | спільний код бутстрапера й лаунчера |
| `launcher-builder` | утиліта збирання маніфестів збірок для CDN |
| `creator-tools` | графічний редактор збірок (з SKCraft) |
| `launcher-fancy` | старий інтерфейс SKCraft, вимкнений у `settings.gradle` |

## Сторонній код

Окрім залежностей, які підтягує Gradle (FlatLaf, Guava, Jackson тощо - кожна під своєю ліцензією), у репозиторії є скопійовані файли з інших проєктів; їхні оригінальні шапки з копірайтом і ліцензією збережені у файлах:

| Файли | Джерело | Ліцензія |
|---|---|---|
| `launcher/.../util/FlatClientProperties.java`, `FlatSystemProperties.java` | [FlatLaf](https://github.com/JFormDesigner/FlatLaf), FormDev Software GmbH | Apache 2.0 ([licenses/Apache-2.0.txt](licenses/Apache-2.0.txt)) |
| `launcher/.../popup/GraphicsUtilities.java`, `ShadowRenderer.java` | [SwingX](https://github.com/ebourg/swingx), Sun Microsystems / Romain Guy | LGPL 2.1+ (GraphicsUtilities - також BSD) |
| `launcher-shared/.../tls/extra-roots.pem` | кореневі сертифікати з `cacerts` Eclipse Temurin (програма CA Mozilla) | публічні сертифікати CA |

## Нотатки для розробників

- Переклади лаунчера: `launcher/src/main/resources/com/gummer/launcher/lang/Launcher_uk_ua.properties` і `Launcher_en_us.properties`; бутстрапера - `launcher-bootstrap/src/main/resources/com/skcraft/launcher/lang/Bootstrap.properties`.
- Вікно та форми: `launcher-flat/src/main/java/com/gummer/launcher/dialog/LauncherFrameFlat.java`; іконки та формати - `launcher/src/main/resources/com/skcraft/launcher`.
- Фон вікна: `launcher/src/main/resources/com/skcraft/launcher/ui/frame/bg.jpg` (1276x718). Власний фон збірки - `bg_<назва збірки>.jpg` поруч (назва з маніфесту, малими літерами, наприклад `bg_staldayz.jpg`, 1276x718, малюється по центру), власний логотип - `logo_<назва збірки>.png` (PNG з прозорістю, вписується без розтягування в зону стандартного logo.png 724x180). Показуються, коли збірку вибрано в списку (`FlatBackgroundPanel.showPack`). Зсуви фону й логотипа збірки (у пікселях) і власні адреси кнопок - `frame_<назва збірки>.properties` поруч: `background.x/y`, `logo.x/y`, `wiki.url`, `telegram.url`, `discord.url`. Картинки кнопок Telegram/Discord зі своєю назвою каналу - `ui/button/telegram_<мова>_<назва збірки>.png` (і `discord_...`).
- Адреси CDN, новин і самооновлення: `launcher/src/main/resources/com/skcraft/launcher/launcher.properties` та `launcher-bootstrap/src/main/resources/com/skcraft/launcher/bootstrap.properties`.
- Кольори: темний `#181818`, світлий `#F2F2F2`; іконки - [boxicons](https://boxicons.com); кнопки бічної панелі 200×48; нікнейм 3–16 символів.
- Налагодження Java-рантаймів: `launcher/src/main/java/com/skcraft/launcher/update/Updater.java`.

---

## English summary

GummerCraft Launcher installs and updates modpacks for the GummerCraft Minecraft servers and launches the game with a Microsoft account. It is a fork of SKCraft Launcher and is licensed under the **GNU LGPL v3** (see [LICENSE.txt](LICENSE.txt)); upstream history lives in the [SKCraft repository](https://github.com/SKCraft/Launcher).

**1.36.4:** all support requests and replies in one chat in the Support window, a window with the reply text when the administration answers (shown before a new crash report if the reply is unread); a "Reinstall to a new folder" button in the crash window when Windows cannot pass the letters of the game path to Java (for example a Cyrillic "Documents"); a Java chosen by hand that is missing or too old for the game version is skipped for that launch.

**1.36.3:** import builds from other launchers (Prism Launcher, PolyMC, ATLauncher, CurseForge, the old Modrinth App, TLauncher, SKlauncher, the official launcher; MultiMC or any other folder by hand) with the game version and mod loader detected; a graphics card choice per build on PCs with two graphics cards (see "what the launcher does on your computer" below); "Delete all builds" and "Delete the launcher and all builds" buttons; Java updates with update and rollback buttons and an optional auto-update; more crash and pre-launch hints (mod for the wrong Minecraft version, incompatible mod pairs, broken mod config, bad Java arguments, corrupted world, old Forge with Cyrillic in the path, roll back a mod after an update); a disk space check before downloads; a clear message when an antivirus blocks a file; a lighter mode without blur and animations on Mac and Linux; faster tab switching in the Build window.

**1.34.1:** fixes: every downloaded file is checked by hash and downloaded again if broken; modpack install no longer runs out of memory on 32-bit Java.

**1.34:** account cabinet shows your skin from whichever server has it (GummerCraft first, then StalDayZ) and the default skin instead of "?", asks before leaving with a chosen but not uploaded skin, checks that the server really saved an upload; server online refreshes every 30 s while the window is active (90 s in the background, none when minimized); the account list puts the last used account first; the crash window opens above the launcher.

**1.33.1:** Ukrainian UI texts edited (informal address, consistent terms, typos), the reinstall prompt for Vanilla and mod loaders shows its question again, anonymous "game started" event (which pack, nothing about the player or computer; `off` in `stats-id.txt` disables it, see SECURITY.md).

**1.33:** new Fabric, Quilt, Forge (1.7.10+, including legacy 1.7.10-1.12.2) and NeoForge (1.20.2+) sections with clean Minecraft and a mod loader; the loader version is pinned and can be changed from the context menu; Forge and NeoForge installers are fetched from their official maven with a sha1 check and their processors run inside the launcher. An optional GummerCraft server account: login, nickname registration, separate StalDayZ and GummerCraft skins, player cabinet (playtime, servers, ranks); only a 30-day token is stored, never the password, and local offline accounts work as before. A "send report to support" button on the disconnect screen opens a prefilled support form in the launcher (needs the updated GummerCraft mods); the support form and error dialogs also work for players on the old exe. Linux: Netty native transport (epoll) disabled to avoid `DecoderException` crashes, crash analysis recognises libGL and GLX problems.

**1.32:** a single `GummerCraft.exe` with bundled Java for new players (silent first-run unpack, no questions, shortcuts or uninstall entry), three sections (StalDayZ, GummerCraft, Vanilla with all Mojang releases since 1.8), a version picker with install-state markers, server online count and "join the server right away", offline play for installed modpacks, friendly error dialogs and crash analysis with in-launcher support chat, download size and speed, Linux fixes (Netty epoll off, libGL/GLX crash hints), an anonymous install ID. Old exes keep working and update themselves.

**1.29 (open-source release):** full HTTPS certificate validation (it used to be disabled as a workaround for outdated Java/Windows root stores, which made the launcher's connections interceptable on untrusted networks; outdated JREs now get a few official root CAs added on top of the system store), launcher downloads over HTTPS only and verified against the SHA-256 published in `release.json`, the exe starts the newest published version right away, log upload over HTTPS, FlatLaf's native DLL no longer extracted to `%TEMP%`, unused telemetry code and the `uuid` setting removed, official Microsoft app id moved out of the source.

Everything lives in one folder (`Documents\GummerCraft` on Windows). The launcher does not write to the registry, with one exception the player turns on: since 1.36.3 the graphics card choice for a build (Build window, shown only on PCs with two graphics cards: "Let the system decide", "name (built-in)", "name (discrete)", the first one by default) writes one value to `HKCU\Software\Microsoft\DirectX\UserGpuPreferences` before the game starts (name: the path to the launcher's own Java, data: `GpuPreference=1;` or `GpuPreference=2;`), exactly what the Windows "Graphics settings" page writes. System Java and choices already made in Windows are left alone, switching back to "Let the system decide" or deleting builds removes the value, and every step is logged. On Linux choosing the discrete card only adds PRIME variables to that one launch. It does not add autostart entries, services or scheduled tasks, or touch files outside that folder. Since 1.36.3, Settings, Advanced, "Delete the launcher and all builds" removes the launcher folders and `bootstrap.yml` and leaves files it did not create alone. Since 1.32 it keeps an anonymous random install ID in `stats-id.txt` (not derived from your name, account or hardware) and sends it only with the modpack list request to our CDN, so we can count players rather than IP addresses. Since 1.33.1 it also tells our CDN which pack was started (our pack name, or the kind and Minecraft version for Vanilla and mod loaders; never your nickname, account, mods or anything about the computer). Write `off` into the file to disable both. Installed modpacks also start without internet (from a saved copy of the modpack list, with the saved Microsoft profile, singleplayer and servers without account checks). Since 1.32 new players can also get a single `GummerCraft.exe` with bundled Java: on first run it unpacks into `%LOCALAPPDATA%\GummerCraft\app` (no shortcuts, no uninstall entry) and then just opens the launcher. Network endpoints, account handling and known limitations are listed in [SECURITY.md](SECURITY.md).

Build with JDK 8: copy `gradle.properties.example` to `gradle.properties`, set `microsoftClientId` to your own Azure app id (the official GummerCraft id is not part of the repository), then run `./gradlew clean build` (add `createAllExecutables` for the Windows exe).
