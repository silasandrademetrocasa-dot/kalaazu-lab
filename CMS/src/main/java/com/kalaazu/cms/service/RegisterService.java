package com.kalaazu.cms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalaazu.model.Version;
import com.kalaazu.persistence.entity.*;
import com.kalaazu.persistence.service.*;
import com.kalaazu.util.Logger;
import com.kalaazu.util.LoggingCategory;
import com.kalaazu.util.StringUtils;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Register service.
 * =================
 * <p>
 * Provides the logic for user registration.
 *
 * @author Manulaiko <manulaiko@gmail.com>
 */
@Service
@RequiredArgsConstructor
public class RegisterService implements Logger {
    @Getter
    private final LoggingCategory category = LoggingCategory.CMS;
    private final UsersService users;
    private final AccountsService accounts;
    private final LevelsService levels;
    private final AccountsHangarsService hangars;
    private final ItemsService items;
    private final AccountsItemsService accountsItems;
    private final AccountsShipsService accountsShips;
    private final AccountsSettingsService accountsSettings;
    private final ShipsService ships;
    private final FactionsService factions;
    private final AccountsConfigurationsService accountsConfigs;
    private final AccountsConfigurationsAccountsItemsService accountsConfigItems;
    private final RanksService ranks;

    private final com.kalaazu.service.ItemsService itemsService;
    private final ObjectMapper mapper;

    /**
     * Performs the user registration.
     *
     * @param username User name.
     * @param password User password.
     * @param email    User email.
     * @return Registered account.
     * @throws Exception If something goes wrong.
     */
    public AccountsEntity register(String username, String password, String email) throws Exception {
        var u = username.trim();
        var p = password.trim();
        var e = email.trim();

        if (u.isEmpty() || p.isEmpty() || e.isEmpty()) {
            throw new Exception("All fields are required!");
        }

        if (!StringUtils.isEmail(e)) {
            throw new Exception("Invalid email!");
        }

        var user = users.register(u, p, e);
        var account = this.createAccount(user);

        var ship = this.createShip(account);
        var hangar = this.createHangar(account, ship);
        var items = this.createItems(account);
        var configs = this.createConfigs(hangar);
        var settings = this.createSettings(account);

        this.addItemsToConfig(items, configs);

        // add base config stats
        configs.forEach((k, c) -> {
            var s = ship.getShipsByShipsId();
            c.setSpeed((short) (c.getSpeed() + s.getSpeed()));
            c.setHealth(c.getHealth() + s.getHealth());
        });

        this.setActiveConfig(configs.values().iterator().next(), hangar);
        this.setActiveHangar(hangar, account);

        account.addShip(ship);
        items.forEach(account::addItem);
        account.addHangar(hangar);
        settings.forEach(account::addSetting);

        return account;
    }

    /**
     * Sets the active configuration of the hangar.
     *
     * @param config Active configuration.
     * @param hangar Configuration owner.
     */
    private void setActiveConfig(AccountsConfigurationsEntity config, AccountsHangarsEntity hangar) {
        hangar.setAccountsConfigurationsByAccountsConfigurationsId(config);

        this.hangars.update(hangar);
    }

    /**
     * Sets the active hangar of the account.
     *
     * @param hangar  Active hangar.
     * @param account Hangar owner.
     */
    private void setActiveHangar(AccountsHangarsEntity hangar, AccountsEntity account) {
        account.setAccountsHangarsByAccountsHangarsId(hangar);

        this.accounts.update(account);
    }

    /**
     * Adds the items to the configurations.
     *
     * @param items   Account items.
     * @param configs Account configurations.
     */
    private void addItemsToConfig(List<AccountsItemsEntity> items, Map<Integer, AccountsConfigurationsEntity> configs) {
        configs.forEach((configId, config) -> items.forEach((item) -> {
            var i = item.getItemsByItemsId();
            if (i.getCategory() != ItemCategory.EQUIPMENT) {
                return;
            }

            var configItem = new AccountsConfigurationsAccountsItemsEntity();
            configItem.setAccountsItemsByAccountsItemsId(item);
            configItem.setAccountsConfigurationsByAccountsConfigurationsId(config);

            this.accountsConfigItems.create(configItem);

            // Update config stats
            switch (i.getType()) {
                case LASER -> config.setDamage(config.getDamage() + i.getBonus());
                case SHIELD_GENERATOR -> config.setShield(config.getShield() + i.getBonus());
                case SPEED_GENERATOR -> config.setSpeed((short) (config.getSpeed() + i.getBonus()));
            }
        }));
    }

    /**
     * Creates the account for the user.
     *
     * @param user User that will own the account.
     * @return Created account.
     */
    private AccountsEntity createAccount(UsersEntity user) {
        var account = new AccountsEntity();
        account.setName(user.getName());
        account.setSessionId(StringUtils.sessionId());

        account.setUsersByUsersId(user);
        account.setLevelsByLevelsId(this.levels.find((byte) 1));
        account.setFactionsByFactionsId(this.factions.find((byte) 1)); // Default to MMO
        account.setRanksByRanksId(this.ranks.find((byte) 1));

        return this.accounts.create(account);
    }

    /**
     * Creates the initial items of the account.
     *
     * @param account Account that will own the items.
     */
    private List<AccountsItemsEntity> createItems(AccountsEntity account) {
        var ret = new ArrayList<AccountsItemsEntity>();
        var items = itemsService.getDefaultStartingItems();

        items.forEach((id, amount) -> {
            var item = new AccountsItemsEntity();
            item.setAmount(amount);
            item.setAccountsByAccountsId(account);
            item.setItemsByItemsId(this.items.findByLootId(id));
            item.setLevelsByLevelsId(this.levels.find((byte) 1));

            var i = this.accountsItems.create(item);

            ret.add(i);
        });

        return ret;
    }

    /**
     * Creates the default account settings.
     *
     * @param account Account that will own the settings.
     */
    
private List<AccountsSettingsEntity> createSettings(AccountsEntity account) {
    var ret = new ArrayList<AccountsSettingsEntity>();

    for (var version : Version.values()) {
        var settings = new AccountsSettingsEntity();

        settings.setAccountsByAccountsId(account);
        settings.setVersion(version.name());

        // Configuracao inicial valida para persistencia.
        settings.setKeybindings(List.of());

        switch (version) {
            case V4 -> settings.setBarState("1");
            case V10 -> settings.setBarState(
                "24,1|23,1|100,1|25,1|35,0|34,0|39,0|"
            );
        }

        // Salva realmente no banco de dados.
        var savedSettings = this.accountsSettings.create(settings);
        ret.add(savedSettings);
    }

    return ret;
}

    /**
     * Creates the ship of the account.
     *
     * @param account Account that will own the ship.
     * @return Created ship.
     */
    private AccountsShipsEntity createShip(AccountsEntity account) {
        var phoenix = this.ships.find((byte) 1); // Phoenix

        var ship = new AccountsShipsEntity();
        ship.setShipsByShipsId(phoenix);
        ship.setHealth(phoenix.getHealth());
        ship.setGfx(phoenix.getGfx());

        ship.setAccountsByAccountsId(account);
        ship.setMapsByMapsId(account.getFactionsByFactionsId().getLowMapsByLowMapsId());
        ship.setPosition(account.getFactionsByFactionsId().getLowMapsPosition());

        return this.accountsShips.create(ship);
    }

    /**
     * Creates the account configurations.
     *
     * @param hangar Account hangar that will own the configuration.
     * @return Created configurations.
     */
    private Map<Integer, AccountsConfigurationsEntity> createConfigs(AccountsHangarsEntity hangar) {
        var configs = new HashMap<Integer, AccountsConfigurationsEntity>();

        for (byte i = 0; i < 2; i++) {
            var config = new AccountsConfigurationsEntity();
            config.setConfigurationId((byte) (i + 1));
            config.setAccountsHangarsByAccountsHangarsId(hangar);

            var c = this.accountsConfigs.create(config);

            configs.put(c.getId(), c);
        }

        return configs;
    }

    /**
     * Creates the hangar of the account.
     *
     * @param account Account that will own the hangar.
     * @param ship    Account ship.
     * @return Created hangar.
     */
    private AccountsHangarsEntity createHangar(AccountsEntity account, AccountsShipsEntity ship) {
        var hangar = new AccountsHangarsEntity();
        hangar.setAccountsByAccountsId(account);
        hangar.setAccountsShipsByAccountsShipsId(ship);

        return this.hangars.create(hangar);
    }
}
