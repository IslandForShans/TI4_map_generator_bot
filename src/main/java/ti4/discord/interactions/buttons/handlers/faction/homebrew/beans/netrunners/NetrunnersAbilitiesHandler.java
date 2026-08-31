package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitKey;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.model.TemporaryCombatModifierModel;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.transaction.SendDebtService;

@UtilityClass
public class NetrunnersAbilitiesHandler {
    public static final String NEURAL_INSTRUMENTS_ABILITY = "neural_instruments";
    public static final String PROXY_NETWORK_ABILITY = "proxy_network";
    public static final String CONTROL_TOKEN_POOL = "hackerman";

    public static void offerNeuralInstruments(Game game, Player techGainer) {
        if (game == null || techGainer == null) return;
        List<Player> netrunners = game.getRealPlayersExcludingThis(techGainer).stream()
                .filter(player -> player.hasAbility(NEURAL_INSTRUMENTS_ABILITY))
                .toList();
        if (netrunners.isEmpty()) return;
        if (game.getDebtPoolIcon(CONTROL_TOKEN_POOL) == null) {
            game.setDebtPoolIcon(CONTROL_TOKEN_POOL, FactionEmojis.netrunners.toString());
        }
        for (Player netrunner : netrunners) {
            if (netrunner.getDebtTokenCount(techGainer.getColor(), CONTROL_TOKEN_POOL) > 0) continue;
            SendDebtService.sendDebt(techGainer, netrunner, 1, CONTROL_TOKEN_POOL);
            MessageHelper.sendMessageToChannel(
                    netrunner.getCorrectChannel(),
                    netrunner.getRepresentationUnfogged() + ", you automatically placed 1 of "
                            + techGainer.getRepresentation(false, true)
                            + "'s command tokens on their faction sheet via **Neural Instruments**."
                            + "\n-# This optional effect was resolved automatically for convenience.");
        }
    }

    public static void addMimeticOverrideButton(List<Button> buttons, Player player, Player opponent) {
        if (player == null || opponent == null || !player.hasTechReady("benetrunnersmo")) return;
        boolean hasSharedTechnology = player.getTechs().stream()
                .filter(opponent::hasTech)
                .map(Mapper::getTech)
                .filter(java.util.Objects::nonNull)
                .anyMatch(tech -> tech.getRequirements().orElse("").length() > 0);
        if (hasSharedTechnology) {
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + "netrunnersMimeticStart",
                    "Use Mimetic Override",
                    FactionEmojis.netrunners));
        }
    }

    @ButtonHandler("netrunnersMimeticStart")
    public static void startMimeticOverride(Game game, Player player, ButtonInteractionEvent event) {
        StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
        if (combat == null || !player.hasTechReady("benetrunnersmo")) return;
        Player opponent = game.getRealPlayers().stream()
                .filter(candidate -> candidate != player && combat.factions().contains(candidate.getFaction()))
                .findFirst()
                .orElse(null);
        if (opponent == null) return;
        List<Button> buttons = player.getTechs().stream()
                .filter(opponent::hasTech)
                .map(Mapper::getTech)
                .filter(java.util.Objects::nonNull)
                .filter(tech -> tech.getRequirements().orElse("").length() > 0)
                .map(tech -> Buttons.green(
                        player.factionButtonChecker() + "netrunnersMimeticTech_" + tech.getAlias(), tech.getName()))
                .toList();
        if (buttons.isEmpty()) return;
        buttons = new java.util.ArrayList<>(buttons);
        buttons.add(Buttons.red(player.factionButtonChecker() + "deleteButtons", "Decline"));
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event, false);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged()
                        + ", you may exhaust _Mimetic Override_ to choose a shared technology and give +1 to 1 ship's combat rolls this round for each prerequisite.",
                buttons);
    }

    @ButtonHandler("netrunnersMimeticTech_")
    public static void chooseMimeticUnit(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String techId = buttonID.replace("netrunnersMimeticTech_", "");
        TechnologyModel tech = Mapper.getTech(techId);
        StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
        if (tech == null || combat == null || !player.hasTechReady("benetrunnersmo") || !player.hasTech(techId)) return;
        String selectedUnitKey = "netrunnersMimeticUnit" + player.getFaction();
        String selectedTechKey = "netrunnersMimeticTech" + player.getFaction();
        if (!techId.equals(game.getStoredValue(selectedTechKey))) {
            game.setStoredValue(selectedTechKey, techId);
            game.removeStoredValue(selectedUnitKey);
        }
        Player opponent = game.getRealPlayers().stream()
                .filter(candidate -> candidate != player && combat.factions().contains(candidate.getFaction()))
                .findFirst()
                .orElse(null);
        Tile tile = game.getTileByPosition(combat.tilePosition());
        UnitHolder holder = tile == null
                ? null
                : tile.getUnitHolders()
                        .get(combat.unitHolderName() == null ? Constants.SPACE : combat.unitHolderName());
        if (opponent == null || !opponent.hasTech(techId) || holder == null) return;
        String selectedValue = game.getStoredValue(selectedUnitKey);
        List<String> selected = selectedValue.isEmpty() ? List.of() : List.of(selectedValue.split(","));
        List<Button> buttons = holder.getUnitKeysForPlayer(player).stream()
                .map(UnitKey::asyncID)
                .distinct()
                .map(asyncId -> player.getPriorityUnitByAsyncID(asyncId, holder))
                .filter(java.util.Objects::nonNull)
                .filter(unit -> unit.getIsShip())
                .filter(unit -> unit.getCombatDieCount() > 0)
                .filter(unit -> !selected.contains(unit.getAsyncId()))
                .map(unit -> Buttons.green(
                        player.factionButtonChecker() + "netrunnersMimeticUnit_" + techId + "|" + unit.getAsyncId(),
                        unit.getUnitEmoji() + " " + unit.getName()))
                .toList();
        if (buttons.isEmpty()) return;
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged()
                        + ", choose a ship whose combat rolls receive +1 from _Mimetic Override_.",
                buttons);
    }

    @ButtonHandler("netrunnersMimeticUnit_")
    public static void resolveMimeticOverride(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("netrunnersMimeticUnit_", "").split("\\|", 2);
        TechnologyModel tech = parts.length == 2 ? Mapper.getTech(parts[0]) : null;
        if (tech == null || !player.hasTechReady("benetrunnersmo")) return;
        int prerequisites = tech.getRequirements().orElse("").length();
        String key = "netrunnersMimeticUnit" + player.getFaction();
        String selected = game.getStoredValue(key);
        selected = selected.isEmpty() ? parts[1] : selected + "," + parts[1];
        if (selected.split(",").length < prerequisites) {
            game.setStoredValue(key, selected);
            ButtonHelper.deleteMessage(event);
            chooseMimeticUnit(game, player, event, "netrunnersMimeticTech_" + parts[0]);
            return;
        }
        var modifier = Mapper.getCombatModifiers().get("netrunners_mimetic_override_1");
        if (modifier == null) return;
        player.exhaustTech("benetrunnersmo");
        game.setStoredValue(key, selected);
        player.addNewTempCombatMod(
                new TemporaryCombatModifierModel("tech", "benetrunnersmo", modifier, player.getNumberOfTurns()));
        game.removeStoredValue("netrunnersMimeticTech" + player.getFaction());
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " exhausted _Mimetic Override_; " + selected
                        + " receive +1 to their combat rolls this round.");
    }

    public static Button getProxyNetworkButton(Game game, Player player) {
        if (game == null || player == null || !player.hasAbility(PROXY_NETWORK_ABILITY)) return null;
        boolean hasEligibleTech = game.getTechnologyDeck().stream()
                .map(Mapper::getTech)
                .filter(java.util.Objects::nonNull)
                .filter(tech -> !player.hasTech(tech.getAlias()))
                .filter(tech -> ti4.service.tech.ListTechService.isTechResearchable(tech, player))
                .anyMatch(tech -> tech.getFaction()
                        .map("netrunners"::equals)
                        .orElseGet(() -> game.getRealPlayersExcludingThis(player).stream()
                                .anyMatch(other -> player.getDebtTokenCount(other.getColor(), CONTROL_TOKEN_POOL) > 0
                                        && tech.getFaction().isEmpty()
                                        && other.hasTech(tech.getAlias()))));
        return hasEligibleTech
                ? Buttons.gray(
                        player.factionButtonChecker() + "netrunnersProxyNetwork",
                        "Use Proxy Network",
                        FactionEmojis.netrunners)
                : null;
    }

    @ButtonHandler("netrunnersProxyNetwork")
    public static void startProxyNetwork(Game game, Player player, ButtonInteractionEvent event) {
        if (getProxyNetworkButton(game, player) == null) return;
        List<Button> buttons = game.getTechnologyDeck().stream()
                .map(Mapper::getTech)
                .filter(java.util.Objects::nonNull)
                .filter(tech -> tech.getFaction()
                        .map("netrunners"::equals)
                        .orElseGet(() -> game.getRealPlayersExcludingThis(player).stream()
                                .anyMatch(other -> player.getDebtTokenCount(other.getColor(), CONTROL_TOKEN_POOL) > 0
                                        && tech.getFaction().isEmpty()
                                        && other.hasTech(tech.getAlias()))))
                .filter(tech -> !player.hasTech(tech.getAlias()))
                .filter(tech -> ti4.service.tech.ListTechService.isTechResearchable(tech, player))
                .map(tech -> Buttons.green(
                        player.factionButtonChecker() + "netrunnersProxyTech_" + tech.getAlias(), tech.getName()))
                .toList();
        if (buttons.isEmpty()) return;
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + ", choose a technology to research via **Proxy Network**.",
                ti4.helpers.NewStuffHelper.buttonPagination(
                        buttons, player.factionButtonChecker() + "netrunnersProxyTech_", 0));
    }

    /** Replaces a non-faction research with the mandatory Proxy Network technology gain. */
    public static boolean interceptProxyNetworkResearch(
            Game game, Player netrunner, ButtonInteractionEvent event, String attemptedTechId) {
        if (game == null
                || netrunner == null
                || !netrunner.hasAbility(PROXY_NETWORK_ABILITY)
                || Mapper.getTech(attemptedTechId) == null
                || "netrunners"
                        .equals(Mapper.getTech(attemptedTechId).getFaction().orElse(""))) {
            return false;
        }
        List<Player> sources = game.getRealPlayersExcludingThis(netrunner).stream()
                .filter(player -> netrunner.getDebtTokenCount(player.getColor(), CONTROL_TOKEN_POOL) > 0)
                .filter(player -> player.getTechs().stream()
                        .map(Mapper::getTech)
                        .anyMatch(tech ->
                                tech != null && tech.getFaction().isEmpty() && !netrunner.hasTech(tech.getAlias())))
                .toList();
        if (sources.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    netrunner.getRepresentationUnfogged()
                            + " cannot research that technology because **Proxy Network** requires a control token and an eligible non-faction technology to copy.");
            return true;
        }
        startProxyNetwork(game, netrunner, event);
        return true;
    }

    @ButtonHandler("netrunnersProxyTech_")
    public static void chooseProxyNetworkSource(
            Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String techId = buttonID.replace("netrunnersProxyTech_", "");
        TechnologyModel tech = Mapper.getTech(techId);
        if (tech == null
                || !player.hasAbility(PROXY_NETWORK_ABILITY)
                || player.hasTech(techId)
                || !ti4.service.tech.ListTechService.isTechResearchable(tech, player)) return;
        if ("netrunners".equals(tech.getFaction().orElse(""))) {
            ti4.service.tech.PlayerTechService.getTech(game, player, event, "getTech_" + techId);
            return;
        }
        List<Button> buttons = game.getRealPlayersExcludingThis(player).stream()
                .filter(source -> player.getDebtTokenCount(source.getColor(), CONTROL_TOKEN_POOL) > 0
                        && tech.getFaction().isEmpty()
                        && source.hasTech(techId))
                .map(source -> Buttons.green(
                        player.factionButtonChecker() + "netrunnersProxySource_" + techId + "|" + source.getFaction(),
                        "Return " + source.getColorDisplayName() + " Token"))
                .toList();
        if (buttons.isEmpty()) return;
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + ", choose the control token to return for **Proxy Network**.",
                buttons);
    }

    @ButtonHandler("netrunnersProxySource_")
    public static void resolveProxyNetwork(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("netrunnersProxySource_", "").split("\\|", 2);
        Player source = parts.length == 2 ? game.getPlayerFromColorOrFaction(parts[1]) : null;
        TechnologyModel tech = parts.length == 2 ? Mapper.getTech(parts[0]) : null;
        if (source == null
                || tech == null
                || !source.hasTech(parts[0])
                || player.hasTech(parts[0])
                || player.getDebtTokenCount(source.getColor(), CONTROL_TOKEN_POOL) < 1) return;
        player.clearDebt(source, 1, CONTROL_TOKEN_POOL);
        ti4.service.tech.PlayerTechService.getTech(game, player, event, "getTech_" + parts[0] + "__proxyNetwork");
    }
}
