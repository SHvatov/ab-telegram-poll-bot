package academy.backend.pollbot.tierlist;

import academy.backend.pollbot.domain.MemeDefinition;
import academy.backend.pollbot.domain.Rating;

import java.util.List;
import java.util.Map;

/**
 * A tier list ready to render: the memes that landed in each tier, keyed by {@link Rating} in
 * S-to-F order. Memes without a rating are simply absent from every tier's list.
 */
public record TierList(Map<Rating, List<MemeDefinition>> tiers) {

    public boolean isEmpty() {
        return tiers.values().stream().allMatch(List::isEmpty);
    }
}
