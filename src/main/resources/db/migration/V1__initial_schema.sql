-- Canary Database Initial Schema for MariaDB/MySQL

CREATE TABLE IF NOT EXISTS `server_config` (
    `config` varchar(50) NOT NULL,
    `value` varchar(256) NOT NULL DEFAULT '',
    CONSTRAINT `server_config_pk` PRIMARY KEY (`config`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `server_config` (`config`, `value`) VALUES 
('db_version', '1'), 
('motd_hash', ''), 
('motd_num', '1'), 
('players_record', '0');

-- Table structure `accounts`
CREATE TABLE IF NOT EXISTS `accounts` (
    `id` int(11) UNSIGNED NOT NULL AUTO_INCREMENT,
    `name` varchar(32) NOT NULL,
    `password` varchar(255) NOT NULL,
    `email` varchar(255) NOT NULL DEFAULT '',
    `premdays` int(11) NOT NULL DEFAULT '0',
    `premdays_purchased` int(11) NOT NULL DEFAULT '0',
    `lastday` int(10) UNSIGNED NOT NULL DEFAULT '0',
    `type` tinyint(1) UNSIGNED NOT NULL DEFAULT '1',
    `coins` int(12) UNSIGNED NOT NULL DEFAULT '0',
    `coins_transferable` int(12) UNSIGNED NOT NULL DEFAULT '0',
    `tournament_coins` int(12) UNSIGNED NOT NULL DEFAULT '0',
    `creation` int(11) UNSIGNED NOT NULL DEFAULT '0',
    `recruiter` int(6) DEFAULT '0',
    `house_bid_id` int(11) NOT NULL DEFAULT '0',
    CONSTRAINT `accounts_pk` PRIMARY KEY (`id`),
    CONSTRAINT `accounts_unique` UNIQUE (`name`),
    INDEX `accounts_email` (`email`),
    INDEX `accounts_password` (`password`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Table structure `players`
CREATE TABLE IF NOT EXISTS `players` (
    `id` int(11) NOT NULL AUTO_INCREMENT,
    `name` varchar(255) NOT NULL,
    `group_id` int(11) NOT NULL DEFAULT '1',
    `account_id` int(11) UNSIGNED NOT NULL DEFAULT '0',
    `level` int(11) NOT NULL DEFAULT '1',
    `vocation` int(11) NOT NULL DEFAULT '0',
    `health` int(11) NOT NULL DEFAULT '150',
    `healthmax` int(11) NOT NULL DEFAULT '150',
    `experience` bigint(20) NOT NULL DEFAULT '0',
    `lookbody` int(11) NOT NULL DEFAULT '0',
    `lookfeet` int(11) NOT NULL DEFAULT '0',
    `lookhead` int(11) NOT NULL DEFAULT '0',
    `looklegs` int(11) NOT NULL DEFAULT '0',
    `looktype` int(11) NOT NULL DEFAULT '136',
    `lookaddons` int(11) NOT NULL DEFAULT '0',
    `maglevel` int(11) NOT NULL DEFAULT '0',
    `mana` int(11) NOT NULL DEFAULT '0',
    `manamax` int(11) NOT NULL DEFAULT '0',
    `manaspent` bigint(20) UNSIGNED NOT NULL DEFAULT '0',
    `soul` int(10) UNSIGNED NOT NULL DEFAULT '0',
    `town_id` int(11) NOT NULL DEFAULT '1',
    `posx` int(11) NOT NULL DEFAULT '1000',
    `posy` int(11) NOT NULL DEFAULT '1000',
    `posz` int(11) NOT NULL DEFAULT '7',
    `cap` int(11) NOT NULL DEFAULT '400',
    `sex` int(11) NOT NULL DEFAULT '0',
    `lastlogin` bigint(20) UNSIGNED NOT NULL DEFAULT '0',
    `lastip` int(10) UNSIGNED NOT NULL DEFAULT '0',
    `save` tinyint(1) NOT NULL DEFAULT '1',
    `deletion` bigint(20) NOT NULL DEFAULT '0',
    CONSTRAINT `players_pk` PRIMARY KEY (`id`),
    CONSTRAINT `players_unique` UNIQUE (`name`),
    INDEX `account_id` (`account_id`),
    INDEX `vocation` (`vocation`),
    CONSTRAINT `players_account_fk`
        FOREIGN KEY (`account_id`) REFERENCES `accounts` (`id`)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Table structure `player_items`
CREATE TABLE IF NOT EXISTS `player_items` (
    `player_id` int(11) NOT NULL DEFAULT '0',
    `pid` int(11) NOT NULL DEFAULT '0',
    `sid` int(11) NOT NULL DEFAULT '0',
    `itemtype` smallint(6) NOT NULL DEFAULT '0',
    `count` smallint(6) NOT NULL DEFAULT '0',
    `attributes` blob DEFAULT NULL,
    INDEX `player_id` (`player_id`),
    INDEX `sid` (`sid`),
    CONSTRAINT `player_items_pk` PRIMARY KEY (`player_id`, `pid`, `sid`),
    CONSTRAINT `player_items_player_fk`
        FOREIGN KEY (`player_id`) REFERENCES `players` (`id`)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
