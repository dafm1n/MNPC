# MNPC

Высокопроизводительный пакетный NPC-движок для **PaperMC 26.2** (Java 25, Mojang Mappings).
Без Citizens, без ProtocolLib, без PacketEvents — только Paper API и NMS через один изолированный адаптер.

## Возможности

- NPC существуют **только на клиенте**: спавн/деспавн через пакеты, ноль нагрузки на мир
- Не занимают слот в таб-листе (`listed = false` в `ClientboundPlayerInfoUpdatePacket`)
- Настоящие скины (texture + signature), асинхронная загрузка с Mojang API + кэш
- Смена имени и скина «на лету», телепортация, поворот головы/тела
- Анимации: Swing Arm (обе руки), Hurt, Critical Hit, Magic Critical Hit
- Полная экипировка: шлем/нагрудник/поножи/ботинки/обе руки
- Автоматическая видимость по радиусу + обработка Join/Quit/смены мира
- Система трейтов: `LookAtPlayerTrait`, `HologramTrait` (TextDisplay), `FollowTrait`
- **Один** центральный тик-таск на все NPC, шардированная проверка видимости — рассчитано на 1000+ NPC
- Клики: `NpcInteractEvent` (LEFT / RIGHT / SHIFT_LEFT / SHIFT_RIGHT) без инъекции в netty —
  через Paper `PlayerUseUnknownEntityEvent`
- YAML-персистентность (`npcs.yml`) с автосохранением и загрузкой при старте

## Структура проекта

```
com.meedix.mnpc
├── MnpcPlugin                  — точка входа, связывает все подсистемы
├── api/                        — публичное API (без NMS-типов)
│   ├── Npc, NpcManager         — основные интерфейсы
│   ├── NpcEquipmentSlot, NpcAnimation
│   ├── event/NpcInteractEvent, event/NpcClickType
│   ├── skin/Skin               — record(texture, signature)
│   └── trait/Trait             — onSpawn / onDespawn / onTick / onRemove
├── core/
│   ├── NpcImpl, NpcManagerImpl, NpcRegistry, NpcAccess
│   ├── tick/NpcTickManager     — единственный scheduler-таск движка
│   └── visibility/VisibilityService — радиус, join/quit, миры, async-подготовка
├── nms/
│   ├── PacketAdapter           — ЕДИНСТВЕННАЯ NMS-поверхность проекта
│   ├── PacketAdapterFactory    — выбор реализации по версии сервера
│   └── v26_2/PacketAdapterImpl — все пакеты для 26.2
├── listener/                   — PlayerConnectionListener, NpcInteractListener
├── skin/SkinService            — асинхронный Mojang API + кэш
├── storage/                    — NpcStorage, YamlNpcStorage, TraitRegistry
├── trait/                      — LookAtPlayerTrait, HologramTrait, FollowTrait
└── command/MnpcCommand         — /npc (живой пример использования API)
```

## Сборка

CI: GitHub Actions собирает проект на каждый push/PR и публикует jar как артефакт (см. `.github/workflows/build.yml`).

NMS-jar Paper не публикуется в публичных Maven-репозиториях, поэтому один раз установите его локально:

```bash
# 1. Скачайте Paper 26.2 (build 27) и распакуйте mojang-mapped сервер
java -Dpaperclip.patchonly=true -jar paper-26.2-27.jar

# 2. Установите server jar и библиотеки Mojang в локальный репозиторий
mvn install:install-file -Dfile=versions/26.2/paper-26.2.jar \
    -DgroupId=io.papermc.paper -DartifactId=paper-server-mojang -Dversion=26.2 -Dpackaging=jar
mvn install:install-file -Dfile=libraries/com/mojang/authlib/9.0.75/authlib-9.0.75.jar \
    -DgroupId=com.mojang -DartifactId=authlib -Dversion=9.0.75 -Dpackaging=jar
mvn install:install-file -Dfile=libraries/com/mojang/datafixerupper/10.0.21/datafixerupper-10.0.21.jar \
    -DgroupId=com.mojang -DartifactId=datafixerupper -Dversion=10.0.21 -Dpackaging=jar

# 3. Сборка
mvn package        # → target/MNPC-1.0.0.jar
```

Jar собирается с манифестом `paperweight-mappings-namespace: mojang`, поэтому Paper не
ремапит плагин при загрузке.

## Использование API

```java
NpcManager npcManager = Bukkit.getServicesManager().load(NpcManager.class);

// Создание
Npc npc = npcManager.createNpc("Guard", location);

// Скин: вручную или с Mojang API
npc.setSkin(texture, signature);
npcManager.fetchSkin("Notch").thenAccept(npc::setSkin);

// Экипировка, телепорт, поворот, анимации
npc.setEquipment(NpcEquipmentSlot.MAIN_HAND, new ItemStack(Material.DIAMOND_SWORD));
npc.teleport(location);
npc.lookAt(player);
npc.playAnimation(NpcAnimation.SWING_MAIN_ARM);

// Трейты
npc.addTrait(new LookAtPlayerTrait(8.0));
npc.addTrait(new HologramTrait(List.of("&6Кузнец", "&7Нажми ПКМ")));
npc.addTrait(new FollowTrait());

// Клики
@EventHandler
public void onNpcClick(NpcInteractEvent event) {
    if (event.getClickType() == NpcClickType.RIGHT_CLICK) {
        event.getPlayer().sendMessage("Привет, я " + event.getNpc().getName());
    }
}

// Удаление
npcManager.removeNpc(npc);
```

## Команда `/npc` (алиас `/mnpc`, permission `mnpc.admin`)

`/npc help` — красивое кликабельное меню всех команд.

`create`, `remove`, `skin`, `tphere`, `lookat`, `anim`, `equiphand`,
`hologram` (строки через `|`), `follow`, `unfollow`,
`togglename` (показать/скрыть имя над головой), `list`, `help`.

Скрытие имени реализовано клиентской scoreboard-командой
(`team` пакет с `nameTagVisibility: never`) — серверный scoreboard
не затрагивается, состояние сохраняется в `npcs.yml`.

## Производительность

- Ни одного `BukkitRunnable` на NPC: один общий тик-таск
- Проверка видимости шардируется по entity id на 10 тиков — при 1000 NPC
  каждый тик обсчитывается ~100 NPC
- Пакеты спавна готовятся и отправляются на виртуальных потоках
  (`Connection#send` потокобезопасен), main thread не блокируется
- Без фейковых `ServerPlayer`: все пакеты собираются из «сырых» значений
- Кэш скинов и дедупликация конкурентных запросов к Mojang API

## Поддержка новых версий

Вся работа с NMS изолирована в `nms/v26_2/PacketAdapterImpl` (~300 строк).
Для новой версии Minecraft реализуйте `PacketAdapter` в новом пакете и добавьте
ветку в `PacketAdapterFactory`.
