package factorization.truth.api;

import net.minecraft.item.ItemStack;

public interface IHtmlTypesetter extends ITypesetter {
    /**
     * Write HTML; use for HTML exporting.
     * @param text Some HTML.
     */
    void html(String text);

    String img(String img);

    String getRoot();

    void putItem(ItemStack item, String link);
}
