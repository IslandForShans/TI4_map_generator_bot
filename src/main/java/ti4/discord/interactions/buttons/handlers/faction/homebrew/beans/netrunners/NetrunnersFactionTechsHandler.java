package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners;

import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.StringHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;

@UtilityClass
public class NetrunnersFactionTechsHandler {
    public static final String DATA_MINING_TECH = "benetrunnersdm";

    public static void resolveDataMining(Game game, Player techGainer, String techId) {
        if (game == null || techGainer == null || Mapper.getTech(techId) == null) {
            return;
        }
        if (!techGainer.hasTech(DATA_MINING_TECH)) {
            return;
        }
        int owners = game.getRealPlayersExcludingThis(techGainer).stream()
                .mapToInt(other -> other.hasTech(techId) ? 1 : 0)
                .sum();
        if (owners < 1) return;
        MessageHelper.sendMessageToChannel(
                techGainer.getCorrectChannel(),
                techGainer.getRepresentation() + " gained "
                        + StringHelper.pluralize(owners, "trade good") + " from **Data Mining** because "
                        + StringHelper.pluralize(owners, "other player") + " also owns "
                        + Mapper.getTech(techId).getNameRepresentation() + ". "
                        + techGainer.gainTG(owners, true));
    }
}
