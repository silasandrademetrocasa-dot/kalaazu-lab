
-- Account's settings table.
--
-- In game settings.
--

CREATE TABLE `accounts_settings`
(
    `id` INT AUTO_INCREMENT COMMENT 'Primary Key.',
    `accounts_id` INT NOT NULL,
    `version` varchar(32) DEFAULT 'V4' NOT NULL,

    `keybindings` TEXT DEFAULT '[{"actionType":7,"charCode":0,"parameter":0,"keys":[49]},{"actionType":7,"charCode":0,"parameter":1,"keys":[50]},{"actionType":7,"charCode":0,"parameter":2,"keys":[51]},{"actionType":7,"charCode":0,"parameter":3,"keys":[52]},{"actionType":7,"charCode":0,"parameter":4,"keys":[53]},{"actionType":7,"charCode":0,"parameter":5,"keys":[54]},{"actionType":7,"charCode":0,"parameter":6,"keys":[55]},{"actionType":7,"charCode":0,"parameter":7,"keys":[56]},{"actionType":7,"charCode":0,"parameter":8,"keys":[57]},{"actionType":7,"charCode":0,"parameter":9,"keys":[48]},{"actionType":8,"charCode":0,"parameter":0,"keys":[112]},{"actionType":8,"charCode":0,"parameter":1,"keys":[113]},{"actionType":8,"charCode":0,"parameter":2,"keys":[114]},{"actionType":8,"charCode":0,"parameter":3,"keys":[115]},{"actionType":8,"charCode":0,"parameter":4,"keys":[116]},{"actionType":8,"charCode":0,"parameter":5,"keys":[117]},{"actionType":8,"charCode":0,"parameter":6,"keys":[118]},{"actionType":8,"charCode":0,"parameter":7,"keys":[119]},{"actionType":8,"charCode":0,"parameter":8,"keys":[120]},{"actionType":8,"charCode":0,"parameter":9,"keys":[121]},{"actionType":0,"charCode":0,"parameter":0,"keys":[74]},{"actionType":1,"charCode":0,"parameter":0,"keys":[67]},{"actionType":2,"charCode":0,"parameter":0,"keys":[17]},{"actionType":3,"charCode":0,"parameter":0,"keys":[32]},{"actionType":4,"charCode":0,"parameter":0,"keys":[69]},{"actionType":5,"charCode":0,"parameter":0,"keys":[82]},{"actionType":13,"charCode":0,"parameter":0,"keys":[68]},{"actionType":6,"charCode":0,"parameter":0,"keys":[76]},{"actionType":9,"charCode":0,"parameter":0,"keys":[72]},{"actionType":10,"charCode":0,"parameter":0,"keys":[70]},{"actionType":11,"charCode":0,"parameter":0,"keys":[107]},{"actionType":12,"charCode":0,"parameter":0,"keys":[109]},{"actionType":14,"charCode":0,"parameter":0,"keys":[13]},{"actionType":15,"charCode":0,"parameter":0,"keys":[9]},{"actionType":16,"charCode":0,"parameter":0,"keys":[16]}]' NOT NULL,

    -- Quality settings

    `quality_not_set`                       TINYINT(1) DEFAULT 0 NOT NULL,
    `quality_attack`                        TINYINT DEFAULT 3 NOT NULL,
    `quality_background`                    TINYINT DEFAULT 3 NOT NULL,
    `quality_presetting`                    TINYINT DEFAULT 3 NOT NULL,
    `quality_poizone`                       TINYINT DEFAULT 3 NOT NULL,
    `quality_ship`                          TINYINT DEFAULT 1 NOT NULL,
    `quality_engine`                        TINYINT DEFAULT 1 NOT NULL,
    `quality_explosion`                     TINYINT DEFAULT 1 NOT NULL,
    `quality_collectables`                  TINYINT DEFAULT 1 NOT NULL,
    `quality_effect`                        TINYINT DEFAULT 1 NOT NULL,
    `quality_customized`                    TINYINT(1) DEFAULT 1 NOT NULL,

    -- Display settings

    `display_not_set`                       TINYINT(1) DEFAULT 0 NOT NULL,
    `display_player_names`                  TINYINT(1) DEFAULT 1 NOT NULL,
    `display_resources`                     TINYINT(1) DEFAULT 1 NOT NULL,
    `show_premium_quickslot_bar`            TINYINT(1) DEFAULT 1 NOT NULL,
    `allow_auto_quality`                    TINYINT(1) DEFAULT 1 NOT NULL,
    `preload_user_ships`                    TINYINT(1) DEFAULT 1 NOT NULL,
    `display_hitpoint_bubbles`              TINYINT(1) DEFAULT 1 NOT NULL,
    `show_not_owned_items`                  TINYINT(1) DEFAULT 1 NOT NULL,
    `display_chat`                          TINYINT(1) DEFAULT 1 NOT NULL,
    `display_windows_background`            TINYINT(1) DEFAULT 1 NOT NULL,
    `display_not_free_cargo_boxes`          TINYINT(1) DEFAULT 1 NOT NULL,
    `drag_windows_always`                   TINYINT(1) DEFAULT 1 NOT NULL,
    `display_notifications`                 TINYINT(1) DEFAULT 1 NOT NULL,
    `hover_ships`                           TINYINT(1) DEFAULT 1 NOT NULL,
    `display_drones`                        TINYINT(1) DEFAULT 1 NOT NULL,
    `display_bonus_boxes`                   TINYINT(1) DEFAULT 1 NOT NULL,
    `display_free_cargo_boxes`              TINYINT(1) DEFAULT 1 NOT NULL,
    `show_minimap_background`               TINYINT(1) DEFAULT 1 NOT NULL,
    `force_2d`                              TINYINT(1) DEFAULT 1 NOT NULL,
    `display_setting_3d_quality_antialias`  TINYINT DEFAULT 4 NOT NULL,
    `display_setting_3d_quality_effects`    TINYINT DEFAULT 4 NOT NULL,
    `display_setting_3d_quality_lights`     TINYINT DEFAULT 4 NOT NULL,
    `display_setting_3d_quality_textures`   TINYINT DEFAULT 4 NOT NULL,
    `display_setting_3d_size_textures`      TINYINT DEFAULT 4 NOT NULL,
    `display_setting_3d_texture_filtering`  TINYINT DEFAULT 4 NOT NULL,
    `pro_action_bar_enabled`                TINYINT(1) DEFAULT 1 NOT NULL,
    `pro_action_bar_keyboard_input_enabled` TINYINT(1) DEFAULT 0 NOT NULL,
    `pro_action_bar_autohide_enabled`       TINYINT(1) DEFAULT 1 NOT NULL,

    -- Window settings

    `hide_all_windows`                      TINYINT(1) DEFAULT 0 NOT NULL,
    `scale`                                 TINYINT(4) DEFAULT 1 NOT NULL,
    `bar_state`                             VARCHAR(255) DEFAULT '23,1,24,1,25,1,26,1,27,1' NOT NULL,

    -- Gameplay settings

    `gameplay_not_set`                      TINYINT(1) DEFAULT 0 NOT NULL,
    `auto_refinement`                       TINYINT(1) DEFAULT 1 NOT NULL,
    `quick_slot_stop_attack`                TINYINT(1) DEFAULT 1 NOT NULL,
    `auto_boost`                             TINYINT(1) DEFAULT 1 NOT NULL,
    `auto_buy_booty_keys`                   TINYINT(1) DEFAULT 1 NOT NULL,
    `double_click_attack_enabled`           TINYINT(1) DEFAULT 1 NOT NULL,
    `auto_change_ammo`                      TINYINT(1) DEFAULT 1 NOT NULL,
    `auto_start_enabled`                    TINYINT(1) DEFAULT 1 NOT NULL,

    -- FIX: Missing column required by Hibernate
    `show_battleray_notifications`          TINYINT(1) DEFAULT 1 NOT NULL,

    `show_low_hp_warn`                      TINYINT(1) DEFAULT 1 NOT NULL,
    `selected_battery`                      TINYINT DEFAULT 1 NOT NULL,
    `selected_rocket`                       TINYINT DEFAULT 1 NOT NULL,

    -- Audio settings

    `audio_not_set`                         TINYINT(1) DEFAULT 0 NOT NULL,
    `play_combat_music`                     TINYINT(1) DEFAULT 0 NOT NULL,
    `voice`                                 TINYINT DEFAULT 100 NOT NULL,
    `sound`                                 TINYINT DEFAULT 100 NOT NULL,
    `music`                                 TINYINT DEFAULT 100 NOT NULL,
    `quests_level_order_descending`         TINYINT(1) DEFAULT 0 NOT NULL,
    `quests_available_filter`               TINYINT(1) DEFAULT 1 NOT NULL,
    `quests_unavailable_filter`             TINYINT(1) DEFAULT 0 NOT NULL,
    `quests_completed_filter`               TINYINT(1) DEFAULT 0 NOT NULL,

    -- Window settings

    `resolution_id`                         TINYINT(4) DEFAULT 3 NOT NULL,
    `minimap_scale`                         TINYINT(4) DEFAULT 1 NOT NULL,

    `window_settings` VARCHAR(255) DEFAULT '0,4,969,1,1,1392,976,1,3,1611,853,1,5,10,10,1,10,4,330,0,13,635,302,0,20,6,447,0,22,795,440,0,23,1694,184,1,24,732,277,0' NOT NULL,

    `quickbar_slot`                         VARCHAR(255) DEFAULT '3,4,5,6,7,39,11,12,13,23' NOT NULL,
    `main_menu_position`                    VARCHAR(255) DEFAULT '806,830' NOT NULL,
    `slot_menu_position`                    VARCHAR(255) DEFAULT '769,798' NOT NULL,
    `slot_menu_order`                       TINYINT(4) DEFAULT 0 NOT NULL,

    CONSTRAINT `accounts_settings_pk` PRIMARY KEY (`id`)

) ENGINE InnoDB
  CHARACTER SET utf8
  COMMENT 'In-game account settings.';

CREATE INDEX `accounts_settings_accounts_id_idx`
    ON `accounts_settings` (`accounts_id`);
