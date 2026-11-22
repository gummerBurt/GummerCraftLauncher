# Зміни GummerCraft Launcher

Усі зміни лаунчера, нові вгорі. Короткий опис лаунчера і посилання на завантаження в [README](README.md), що він робить у мережі і на комп'ютері в [SECURITY.md](SECURITY.md).

*English history is at the bottom: [English changelog](#english-changelog).*

## 1.36.11

- **Пам'ять і Java збірки одразу у вікні «Збірка».** Нова картка першою у вкладці «Загальне»: своя пам'ять для цієї збірки, вибір Java і кнопка «Аргументи JVM». Картка пише, скільки пам'яті отримає гра і чому.
- **Пам'ять, яку ти вибрав, тепер точна.** Якщо ти сам поставив пам'ять (у збірці чи в налаштуваннях лаунчера), лаунчер більше не піднімає її порадою за кількістю модів. Якщо не ставив, лаунчер дає щедру пораду, але не більше половини пам'яті ПК. Якщо вибрано точно замало для збірки (наприклад, 1 ГБ на сотні модів), лаунчер дає мінімум і пише про це. Повзунок пам'яті тепер з кроком 0,5 ГБ.
- **Дискретна відеокарта за замовчуванням.** На ноутбуках і ПК з двома відеокартами гра тепер запускається на дискретній, лаунчер перевіряє відеокарти перед кожним запуском. Інакше можна вибрати у вікні «Збірка», картка «Відеокарта».
- **Менше мікрофризів на Minecraft 1.18 і новіших:** лаунчер налаштовує збирач пам'яті Java (G1), якщо ти не задав свій в «Аргументах JVM».
- **Розділи «Встановлених» можна згорнути** кліком по підпису (ГаммерКрафт, Ваніль, Fabric та інші), стан зберігається.

## 1.36.10

- **Імпорт з інших лаунчерів переносить збірку повністю.** Модпаки SKlauncher, TLauncher та офіційного лаунчера, що мають свою теку гри, тепер копіюються цілком: оформлення меню модпака, квести, дані модів у їхніх власних теках (CustomNPCs і подібні). Раніше переносились лише моди, налаштування, світи, ресурспаки і шейдери. Із самої теки `.minecraft` теж береться все, крім файлів гри та лаунчерів.

## 1.36.9

- **Мова за мовою системи.** Новий лаунчер (без збереженої мови) вибирає її сам: українська, якщо мова чи регіон Windows українські або російські, інакше англійська. Хто вже вибрав мову, у того нічого не зміниться.
- **«Додати»:** ваніль і завантажувачі показують лише версії гри, без назв твоїх встановлених збірок і без зелених стрілок оновлень (вони тепер лише у «Встановлених»). Якщо така версія вже стоїть, лаунчер питає: відкрити встановлену чи поставити ще одну окрему.
- **Розбір вильоту:** пошкоджений файл налаштувань самого Forge (`config/fml.toml`, гра закривається одразу після запуску) лаунчер тепер розпізнає і пропонує скинути його однією кнопкою.
- **Steam Deck:** в ігровому режимі після виходу з гри знову видно лаунчер, а не сірий екран: там вікно лаунчера на час гри більше не закривається.
- **Steam Deck:** ярлик, який додає установка, одразу з'являється в «Нещодавніх» на головній ігрового режиму, а не лише в бібліотеці (для нової установки ярлика).

## 1.36.8

- **Статус у Discord.** Поки лаунчер відкритий, друзі в Discord бачать «Грає в GummerCraft»: у лаунчері чи в якій збірці і скільки часу, з кнопками «Скачати лаунчер» і «Discord GummerCraft»; у StalDayZ своя картинка. Лаунчер говорить лише з Discord на твоєму ПК, нічого не йде в мережу. Вимикається в налаштуваннях: «Додатково», «Статус у Discord». На Linux і Steam Deck працює з новою установкою (Java 16+).
- **Steam Deck: керування геймпадом.** Установка для Linux і Steam Deck тепер ставить ярлику GummerCraft у Steam розкладку геймпада «GummerCraft (лаунчер і гра)»: в іграх ходьба лівим стіком, камера правим стіком чи тачпадом, R2 / L2 удар і використати, A стрибок, B присісти та інше (повна таблиця в [tools/linux](tools/linux/README.md)), а в лаунчері хрестовина і лівий стік ходять по кнопках і збірках, A натискає, B закриває вікна, правий тачпад рухає курсор. Свою розкладку гравця установка не змінює.
- **Steam Deck: лаунчер з геймпада.** Під Steam на Linux лаунчер ходить стрілками до найближчого елемента (кнопки, плашки, список збірок, поля, вкладки, вікна) і показує зелену рамку навколо вибраного. На Windows і macOS усе як раніше.
- **Steam Deck:** ярлик у бібліотеці Steam тепер справді додається (раніше установка лише просила Steam додати його, і Steam міг це мовчки проігнорувати).
- Виправлено: після виходу з гри в ігровому режимі Steam Deck замість лаунчера міг бути сірий екран.
- Виправлено: стрілка вниз у списку збірок більше не відкриває вікно «Імпорт збірки», рядок імпорту відкривається лише кліком.

## 1.36.7

- **Експорт Vanilla:** встановлену ванільну збірку тепер можна зберегти в `.mrpack` (у файлі лише версія гри, за бажанням ресурспаки). Раніше вкладка «Імпорт/Експорт» писала, що збірка не встановлена, хоч вона давно стояла.
- Виправлено: у Liquid Glass напис «UA» на перемикачі мови впирався в праву рамку. Прапорець і текст тепер стоять по центру з однаковими відступами.
- Підказки лаунчера більше не просять «натиснути Грати» там, де кнопка зветься «Запуск»: замість цього нейтральне «запусти збірку».
- **Steam Deck:** установка для Linux і Steam Deck тепер сама ставить обкладинки GummerCraft у бібліотеку Steam (кавер, широка плитка, шапка і логотип). Свої обкладинки гравця не перезаписуються. Хто вже встановив лаунчер, може ще раз запустити той самий файл `GummerCraft-Linux.desktop`, збірки і світи лишаться.
- Опис лаунчера (README) переписано під поточний стан, а всі зміни версій перенесено в окремий файл CHANGELOG.md.

## 1.36.6

- Виправлено: якщо лаунчер працює на старій Java 8 (старий `Gummer_Craft_launcher.exe` без вбудованої Java), після 1.36.5 весь текст був приблизно вдвічі більшим, назви обрізались («Встан…», «Скас…»), а рядки списку налазили один на одний. Шрифт Inter тепер вбудований у лаунчер у форматі TrueType, який стара Java малює правильно, тож новий шрифт працює і там.
- Лаунчер на старті сам перевіряє, що шрифт Inter має правильні розміри. Якщо ні, бере Segoe UI. Що вибрано і чому, видно в журналі лаунчера (рядок `UI font`).
- Виправлено: у списку збірок Liquid Glass другий рядок («доступне оновлення») міг стояти помітно нижче назви.
- Анонімна статистика запуску гри тепер містить Java, на якій працює сам лаунчер (виробник, версія, вбудована чи своя), і вибраний шрифт, щоб такі проблеми було видно раніше за скарги. Подробиці в [SECURITY.md](SECURITY.md).

## 1.36.5

- **Дві вкладки над списком збірок:** ліворуч «Встановлені», праворуч «Додати +». Лаунчер відкривається на «Встановлених», а якщо там ще нічого немає, одразу на «Додати +».
- **«Додати +» лише встановлює.** Vanilla, Fabric, Quilt, Forge, NeoForge та модпаки Modrinth там тільки ставляться. Якщо така версія вже є, поряд з'явиться ще одна, наприклад «Forge 1.16.5 (2)», а під кнопкою видно, скільки таких уже встановлено. Назву інсталяції можна змінити у вікні «Збірка», а видалити її правим кліком у «Встановлених».
- **Сервери GummerCraft і StalDayZ** у «Додати +»: не встановлена версія з кнопкою «Встановити», встановлена одразу з «Запуском».
- **Після встановлення** лаунчер сам переходить у «Встановлені» на нову збірку. Гра після встановлення більше не запускається сама: натисни «Запуск», коли будеш готовий.
- **Liquid Glass:** у не встановленої збірки кнопка «Встановити» замість «Запуску», на тому самому місці.
- **Бібліотека:** у вузькому вікні кнопки «Налаштування», «Вікі», «Підтримка» стискаються до значків (назва в підказці), тож нічого не виїжджає за край. Вибір вигляду тепер є і на сторінці Modrinth, і коли ще нічого не встановлено.
- **Новий шрифт, чіткіший текст:** весь лаунчер тепер пише шрифтом Inter, зробленим спеціально для екранів, і згладжує його так само, як Windows (ClearType). Раніше на масштабі екрана 125% і подібних літери стискались, відступи між ними були нерівні, і текст наче мерехтів.
- **Чіткі іконки:** значок акаунта, Telegram, Discord та іконки меню правого кліку тепер векторні, решта значків рівняється по пікселях екрана, прапорці UA / EN рівніші, а піксельні кнопки (Telegram, Discord, «Підтримати», «Запуск») більше не розмиваються на масштабі 125% і 150%.
- **Esc закриває вікна:** налаштування, акаунт, «Збірка» та інші вікна лаунчера закриваються клавішею Esc, як хрестиком.
- **Менше пам'яті під час гри:** поки йде гра, лаунчер звільняє готові картинки фонів і віддає пам'ять системі (на нашому тесті приблизно на 120 МБ менше).
- Виправлено: жовтий напис біля лого Liquid Glass у малому вікні налазив на прапорці UA / EN і плашку акаунта; кнопки лайку і «Фідбек по дизайну» стоять ближче до «Вигляд» і не вилазять за бічну панель; рамки кнопок і плашок (лайк, акаунт, прапорці, вибрана збірка, сповіщення, картки новин) на масштабі 125% і 150% більше не зрізані зверху чи знизу; кнопка відміни всюди «Скасувати» (і в стандартних вікнах підтвердження замість англійських Cancel, Yes, No); другий рядок у списку збірок Liquid Glass («Усі версії та модпаки») стоїть рівно під назвою.
- Виправлено: логотип GummerCraft стояв то вище, то нижче залежно від версії Main.
- Виправлено: у Класиці кнопка «Збірка» стоїть по центру кнопки «ГРАТИ», а прапорці UA / EN такої ж висоти, як сусідні кнопки; зелена рамка вибору з'являється лише тоді, коли ходиш клавішами Tab чи стрілками (раніше одразу після запуску стояла на першій кнопці); у вікні «Підтримка» поле для питання займає всю висоту вікна, без порожнечі під «Надіслати».
- Виправлено: якщо гру закрили через диспетчер завдань, лаунчер міг помилково назвати винним мод, про який у журналі гри було лише звичайне попередження.
- Виправлено: у меню правого кліку по порожньому місцю списку зверху була зайва смужка.
- Виправлено: «Оновлення Java» пропонувало оновитись на ту саму версію, що вже стоїть.
- Виправлено: на екрані відключення від сервера (GummerCraft на новій версії Minecraft) кнопка «Надіслати звіт у техпідтримку» могла з'являтись двічі.

## 1.36.4

- **Усі звернення в підтримку в одному чаті:** у вікні «Підтримка» видно всі свої звернення та відповіді адміністрації однією стрічкою, тож відповідь на старе звернення більше не загубиться.
- **Відповідь підтримки видно одразу:** коли адміністрація відповіла, лаунчер показує вікно з текстом відповіді. Якщо після вильоту відповідь ще не переглянута, спершу покаже її, а вже потім нове звернення.
- **Кирилиця в шляху до гри:** якщо гра не запускається, бо Windows не передає Java літери в шляху (наприклад «Документи»), у вікні вильоту є кнопка «Перевстановити до нової теки».
- Java, вибрана в налаштуваннях вручну, не заважає запуску, якщо її видалили або вона застара для цієї версії гри: тоді на цей запуск береться Java лаунчера.

## 1.36.3

- **Імпорт збірок з інших лаунчерів:** рядок «Імпорт збірки» під Modrinth у списку збірок та вкладка «Імпорт/Експорт» у вікні «Збірка». Лаунчер сам знаходить збірки Prism Launcher, PolyMC, ATLauncher, CurseForge, Modrinth App (стара версія), TLauncher, SKlauncher та офіційного лаунчера Minecraft, визначає версію гри та завантажувач і переносить моди, налаштування, ресурспаки та шейдери в нову збірку, а світи за бажанням. Теку MultiMC чи будь-якого іншого лаунчера можна вибрати вручну. Нового Modrinth App поки немає: він зберігає збірки у своїй базі.
- **Вибір відеокарти** (вікно «Збірка», «Загальне», лише на ПК з двома відеокартами): «Як вирішить система», інтегрована або дискретна, з назвами відеокарт. Що при цьому змінюється на комп'ютері, чесно описано в розділі [«Що лаунчер робить на комп'ютері»](README.md#що-лаунчер-робить-на-компютері).
- **Видалення одним кліком:** у налаштуваннях, «Додатково», дві червоні кнопки «Видалити всі збірки» та «Видалити лаунчер та всі збірки». Лаунчер покаже, скільки місця звільниться, попросить підтвердження та видалить лише свої файли.
- **Оновлення Java** (налаштування, «Java та пам'ять»): видно, яка Java стоїть у лаунчера та у версій гри і чи є новіша. Кнопки «Оновити» та «Відкатити», а автооновлення Java можна вимкнути.
- **Помічник знає більше.** Перед запуском попереджає про мод не під ту версію Minecraft та про пари модів, що не працюють разом. Після вильоту розпізнає зламаний конфіг мода, неправильні параметри Java, пошкоджений світ та старий Forge з кирилицею в шляху. Мод, що почав вилітати після оновлення, можна повернути на попередню версію однією кнопкою.
- **Місце на диску** перевіряється перед завантаженням: якщо не вистачає, лаунчер скаже, скільки треба звільнити і на якому диску.
- **Антивірус заблокував файл:** лаунчер так і скаже та підкаже, що робити, замість незрозумілої помилки.
- **Спрощений режим на Mac та Linux:** без розмиття та анімацій, тож лаунчер не гальмує. На Linux звичайна рамка вікна. Вигляди та всі функції ті самі.
- **Швидше:** вкладки вікна «Збірка» перемикаються без затупів навіть у великих збірках, а головне вікно відкривається швидше.
- Порожній список «Встановлені» тепер пропонує перейти до всіх збірок або імпортувати збірку з іншого лаунчера.

## 1.36

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

## 1.35

- Три вигляди лаунчера на вибір: **Liquid Glass** (за замовчуванням), **Класика** та **Бібліотека**. Перемикаються прямо в головному вікні (блок «Вигляд» унизу) або в налаштуваннях. У кожного вигляду свої вікна налаштувань, підтримки, акаунта та свої сповіщення.
- Дизайну, котрий подобається, можна поставити лайк (один з IP на вигляд), а кнопка «Фідбек по дизайну» відкриває розмову з нами.
- Налаштування лаунчера по-новому: розділи «Загальні», «Java та пам'ять», «Додатково», з прокруткою, тож усе видно і на малому екрані.
- Відповідь підтримки видно одразу: число непрочитаних на «Підтримці» оновлюється щохвилини, а не лише при старті лаунчера.
- Якщо лаунчер не зміг запуститись, вікно «Лаунчер не запустився» пояснює, що робити, і дає скопіювати деталі для підтримки.
- Перша установка гри швидша та надійніша: файли Minecraft (звуки, текстури, бібліотеки) качаються кількома великими архівами з нашого CDN, а не тисячами дрібних файлів від Mojang. Обірвався інтернет чи зникло світло? Наступного разу лаунчер докачає архів з того ж місця. Кожен файл перевіряється, тимчасові файли прибираються. Під час оновлення, як і раніше, завантажуються лише зміни.
- «Музей» у розділі Vanilla: версії, старіші за 1.8, аж до найпершої rd-132211, а також Classic, Infdev, Alpha та Beta. Запускаються, але звуків і скінів у найстаріших версій немає: сервери Mojang для них давно вимкнені.
- Лог лаунчера тепер зберігається у файлі `logs/launcher.log` (+ два попередні запуски), тож проблему можна розібрати навіть після перезапуску. У звернення в підтримку з галочкою «Додати лог» він додається автоматично.
- Анонімна подія «завантаження збірки»: скільки йшла установка та з якого джерела гальмувало. Без ніка, акаунта та назв файлів. Вимикається тим самим `off` у `stats-id.txt`. Подробиці в [SECURITY.md](SECURITY.md).

## 1.34.1

Виправлення: лаунчер перевіряє кожен завантажений файл і перекачує зіпсований, встановлення модпака на 32-бітній Java більше не падає з нестачею пам'яті.

## 1.34

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

## 1.33.1

- Тексти лаунчера українською відредаговано: звертання на «ти», однакові терміни, виправлено описки.
- У діалозі перевстановлення Vanilla і завантажувачів знову видно питання «Бажаєш продовжити?».
- Анонімна подія «гру запущено» (яку збірку запущено, без ніка, акаунта і даних про комп'ютер), вимикається словом `off` у `stats-id.txt`. Подробиці в [SECURITY.md](SECURITY.md).

## 1.33

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

## 1.32

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

## 1.31

- Службова DLL бібліотеки інтерфейсу FlatLaf тепер справді не розпаковується в тимчасову папку Windows (виправлення з 1.29 спрацьовувало не завжди: FlatLaf вантажив DLL раніше, ніж отримував шлях до папки лаунчера).
- Інсталятор визначає папку "Документи" напряму, без вікна вибору файлів Java, яке побічно перебирало ярлики на робочому столі.
- Якщо на комп'ютері немає Java, інсталятор качає її прямо з сайту Oracle, без проміжної переадресації через наш сайт.
- Скрипт `tools/verify-cdn.py`: будь-хто може однією командою перевірити, що офіційний лаунчер на CDN зібраний із цього коду (дивись [«Перевірка офіційної збірки»](README.md#перевірка-офіційної-збірки)).
- Прибрано невикористаний клас `PastebinPoster` з оригінального SKCraft (логи вивантажуються лише на наш сервер логів, кнопкою в консолі).

## 1.30

- Фон і логотип вікна лаунчера міняються разом із вибраною збіркою, з плавним переходом: у StalDayZ свій фон і логотип S.T.A.L.DAYZ, у решти - звичні.
- Кнопки «Вікі та FAQ» і «Новини та чат» для StalDayZ ведуть на вікі і Telegram-канал StalDayZ.
- Лаунчер пам'ятає останню вибрану збірку і одразу відкривається з нею та її оформленням.
- Оновлення лаунчера ставляться самі, без питань: при запуску лаунчер завантажує нову версію (з перевіркою SHA-256) і перезапускається.

## 1.29

Разом із відкриттям коду ми провели аудит лаунчера і виправили знайдене:

- **Увімкнено повну перевірку HTTPS-сертифікатів.** Раніше її було вимкнено як обхідне рішення для старих Windows і Java, яким бракувало нових кореневих сертифікатів. Через це в незахищених мережах (наприклад, публічний Wi-Fi) з'єднання лаунчера теоретично можна було перехопити. Тепер кожне з'єднання перевіряється повністю, а для старих Java лаунчер додає кілька офіційних кореневих сертифікатів до системних (див. `TrustedTls`).
- **Лаунчер і його оновлення завантажуються тільки по HTTPS і звіряються з контрольною сумою SHA-256**, опублікованою в `release.json`.
- **Exe одразу запускає найновішу версію:** при кожному старті він звіряє встановлену версію з опублікованою і, якщо вийшла новіша, ставить її до запуску. Без інтернету запускається встановлена.
- **Лог консолі відправляється по HTTPS** (лише після натискання кнопки «Завантажити лог»).
- **Бібліотека інтерфейсу FlatLaf більше не розпаковує DLL у тимчасову папку Windows** - файл лежить у папці лаунчера.
- **Прибрано невикористаний код телеметрії та ідентифікатор `uuid` з налаштувань.** Лаунчер не збирає і не відправляє даних про комп'ютер. (З 1.32 є анонімний випадковий ID установки для підрахунку гравців, з 1.33.1 анонімна подія «гру запущено» з назвою збірки, див. [SECURITY.md](SECURITY.md).)
- **Офіційний ID застосунку Microsoft винесено з коду** в налаштування збірки.

---

## English changelog

**1.36.11:** memory and Java for a build right in the Build window (a new card at the top of General: own memory, Java, JVM arguments, and how much memory the game gets and why); the memory you chose is now exact and no longer raised by the mod count advice, while unset memory gets a generous recommendation of at most half the PC memory, and a choice that is surely too little for the build (like 1 GB for hundreds of mods) is raised to a minimum with a note; the memory slider moves in 0.5 GB steps; on PCs with two graphics cards the game now starts on the discrete one by default, checked before every launch (can be changed in the Build window, Graphics card); fewer micro-stutters on Minecraft 1.18+ with a tuned G1 garbage collector unless you set your own in JVM arguments; sections of "Installed" collapse with a click on their caption, and the state is kept.

**1.36.10:** import from other launchers copies the whole build: SKlauncher, TLauncher and official launcher modpacks with their own game folder are copied entirely (modpack menu design, quests, mod data in mods' own folders such as CustomNPCs); before only mods, configs, worlds, resource packs and shaders were copied. From the shared `.minecraft` everything is taken except game and launcher files.

**1.36.9:** the launcher language for a new data folder follows the system (Ukrainian if the Windows display language or region is Ukrainian or Russian, English otherwise; a language already chosen is kept); in "Add" vanilla and loaders show only game versions, without the names of your installed builds and without update arrows (those are in "Installed" only), and installing a version you already have asks whether to open the installed one or add one more; a damaged Forge settings file (`config/fml.toml`, the game closes right after launch) is now recognised with a one-click reset; Steam Deck Game Mode: the launcher is shown again after leaving the game instead of a grey screen (its window is no longer closed while the game runs there), and the shortcut added by the installer shows up in Recent games on the Game Mode home screen.

**1.36.8:** Discord status (Rich Presence): while the launcher is open, Discord friends see "Playing GummerCraft", in the launcher or which build and for how long, with "Download the launcher" and "GummerCraft Discord" buttons (StalDayZ has its own picture); only local IPC with Discord on this PC, can be turned off in Settings, Advanced (Linux and Steam Deck need Java 16+, i.e. the new installer); Steam Deck gamepad support: the Linux and Steam Deck installer gives the GummerCraft Steam shortcut a "GummerCraft (launcher and game)" controller layout (in game: left stick to walk, right stick or trackpad to look, R2 / L2 attack and use, A jump, B sneak and more, full table in [tools/linux](tools/linux/README.md); in the launcher: D-pad and left stick move between buttons and builds, A presses, B closes windows, the right trackpad moves the cursor), a layout the player already chose is kept; under Steam on Linux the launcher navigates with arrow keys to the nearest element and draws a focus ring (Windows and macOS unchanged); the Steam shortcut is now really added (the installer used to only ask Steam to add it, which Steam could silently ignore); fixed a grey screen instead of the launcher after leaving the game in Steam Deck Game Mode; the down arrow in the build list no longer opens the "Import build" window.

**1.36.7:** installed Vanilla builds can be exported to `.mrpack` (Minecraft version only, resource packs optional), instead of "not installed"; the UA label on the Liquid Glass language switch no longer touches the right border; hints no longer ask to press "Play" where the button is called "Launch"; the Linux and Steam Deck installer now adds GummerCraft artwork to the Steam library (cover, wide capsule, hero, logo) without replacing artwork the player set; the README was rewritten for the current state and the full history moved to CHANGELOG.md.

**1.36.6:** fixed text about twice too large (cut-off labels, overlapping rows) when the launcher runs on Java 8 (the old `Gummer_Craft_launcher.exe` without bundled Java): Inter is now bundled as TrueType (converted from the Inter 4.0 OTF, whose CFF outlines Oracle Java 8 rendered about twice too large), so Java 8 gets Inter too; the launcher checks the Inter font metrics on start and falls back to Segoe UI if they are off (`UI font` line in the launcher log); the second line of a Liquid Glass list item no longer sits too low; the anonymous launch statistic now includes the launcher Java (vendor, major version, bundled or system) and the chosen UI font.

**1.36.5:** new UI font Inter and system ClearType text on Windows (Segoe UI with forced grayscale antialiasing looked squeezed at 125% scaling); sharp icons at 125-150% scaling (vector account, Telegram, Discord and context menu icons, pixel-snapped glyphs and flags, sharp pixel-art buttons); Esc closes launcher dialogs; the Liquid Glass splash no longer overlaps the UA / EN flags and account chips, the like and design feedback buttons sit closer to the look switch and stay inside the sidebar, rounded borders (like button, account chips, flags, selected pack, toasts, news cards) are no longer clipped at 125-150% scaling, a game closed from Task Manager no longer blames a mod that only logged a harmless warning, the launcher frees background images while the game runs (about 120 MB less memory), Classic: the build button is centred on PLAY and the flags match the button height, the keyboard focus ring shows only after Tab or arrow keys, the support form fills the window, standard confirm dialogs use the launcher language for OK / Cancel / Yes / No, list subtitles are aligned with titles; the GummerCraft logo no longer jumps between Main versions; two tabs above the build list, "Installed" (left, opens on start) and "Add +" (opens on start when nothing is installed yet); "Add +" only installs: Vanilla, Fabric, Quilt, Forge, NeoForge and Modrinth modpacks are added as a new installation each time ("Forge 1.16.5 (2)" next to an existing one, with a count under the button), the GummerCraft and StalDayZ servers show "Install" or "Play"; after installing the launcher switches to "Installed" on the new build and no longer starts the game by itself; an "Install" button in Liquid Glass; in the Library the top buttons shrink to icons in a narrow window and the look picker is shown on the Modrinth page and with no builds installed; fixes: the Java updates tile no longer offers the same version, and the "Send a report to support" button no longer appears twice on the disconnect screen.

**1.36.4:** all support requests and replies in one chat in the Support window, a window with the reply text when the administration answers (shown before a new crash report if the reply is unread); a "Reinstall to a new folder" button in the crash window when Windows cannot pass the letters of the game path to Java (for example a Cyrillic "Documents"); a Java chosen by hand that is missing or too old for the game version is skipped for that launch.

**1.36.3:** import builds from other launchers (Prism Launcher, PolyMC, ATLauncher, CurseForge, the old Modrinth App, TLauncher, SKlauncher, the official launcher; MultiMC or any other folder by hand) with the game version and mod loader detected; a graphics card choice per build on PCs with two graphics cards (see "What the launcher does on your computer" in the README); "Delete all builds" and "Delete the launcher and all builds" buttons; Java updates with update and rollback buttons and an optional auto-update; more crash and pre-launch hints (mod for the wrong Minecraft version, incompatible mod pairs, broken mod config, bad Java arguments, corrupted world, old Forge with Cyrillic in the path, roll back a mod after an update); a disk space check before downloads; a clear message when an antivirus blocks a file; a lighter mode without blur and animations on Mac and Linux; faster tab switching in the Build window.

**1.34.1:** fixes: every downloaded file is checked by hash and downloaded again if broken; modpack install no longer runs out of memory on 32-bit Java.

**1.34:** account cabinet shows your skin from whichever server has it (GummerCraft first, then StalDayZ) and the default skin instead of "?", asks before leaving with a chosen but not uploaded skin, checks that the server really saved an upload; server online refreshes every 30 s while the window is active (90 s in the background, none when minimized); the account list puts the last used account first; the crash window opens above the launcher.

**1.33.1:** Ukrainian UI texts edited (informal address, consistent terms, typos), the reinstall prompt for Vanilla and mod loaders shows its question again, anonymous "game started" event (which pack, nothing about the player or computer; `off` in `stats-id.txt` disables it, see SECURITY.md).

**1.33:** new Fabric, Quilt, Forge (1.7.10+, including legacy 1.7.10-1.12.2) and NeoForge (1.20.2+) sections with clean Minecraft and a mod loader; the loader version is pinned and can be changed from the context menu; Forge and NeoForge installers are fetched from their official maven with a sha1 check and their processors run inside the launcher. An optional GummerCraft server account: login, nickname registration, separate StalDayZ and GummerCraft skins, player cabinet (playtime, servers, ranks); only a 30-day token is stored, never the password, and local offline accounts work as before. A "send report to support" button on the disconnect screen opens a prefilled support form in the launcher (needs the updated GummerCraft mods); the support form and error dialogs also work for players on the old exe. Linux: Netty native transport (epoll) disabled to avoid `DecoderException` crashes, crash analysis recognises libGL and GLX problems.

**1.32:** a single `GummerCraft.exe` with bundled Java for new players (silent first-run unpack, no questions, shortcuts or uninstall entry), three sections (StalDayZ, GummerCraft, Vanilla with all Mojang releases since 1.8), a version picker with install-state markers, server online count and "join the server right away", offline play for installed modpacks, friendly error dialogs and crash analysis with in-launcher support chat, download size and speed, Linux fixes (Netty epoll off, libGL/GLX crash hints), an anonymous install ID. Old exes keep working and update themselves.

**1.29 (open-source release):** full HTTPS certificate validation (it used to be disabled as a workaround for outdated Java/Windows root stores, which made the launcher's connections interceptable on untrusted networks; outdated JREs now get a few official root CAs added on top of the system store), launcher downloads over HTTPS only and verified against the SHA-256 published in `release.json`, the exe starts the newest published version right away, log upload over HTTPS, FlatLaf's native DLL no longer extracted to `%TEMP%`, unused telemetry code and the `uuid` setting removed, official Microsoft app id moved out of the source.
