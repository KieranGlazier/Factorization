package factorization.truth.word;

import factorization.truth.DocViewer;
import factorization.truth.WordPage;
import factorization.truth.api.IHtmlTypesetter;
import factorization.util.ItemUtil;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class ItemWord extends Word {
    public ItemStack is = null;
    public ItemStack[] entries = null;

    static ItemStack sanitize(ItemStack is) {
        if (is == null) {
            return new ItemStack(Blocks.fire);
        }
        if (is.getItem() != null) {
            return is;
        }
        return new ItemStack(Blocks.fire);
    }

    public ItemWord(ItemStack is) {
        this.is = is;
        cleanWildlings();
    }
    public ItemWord(ItemStack[] entries) {
        if (entries.length == 0) entries = null;
        this.entries = entries;
        cleanWildlings();
    }

    public ItemWord(Collection<ItemStack> entries) {
        this(entries.toArray(new ItemStack[entries.size()]));
    }

    public void setDefaultLink() {
        if (is != null) setLink(getDefaultHyperlink(is));
        else if (entries != null) setLink(getDefaultHyperlink(entries));
    }

    static String getDefaultHyperlink(ItemStack is) {
        if (is == null) return null;
        if (is.getItem() == null) {
            return null;
        }
        if (ItemUtil.isWildcard(is, false)) {
            List<ItemStack> sub = ItemUtil.getSubItems(is);
            if (sub.isEmpty()) {
                return null;
            }
            is = sub.get(0);
        }
        return "cgi/recipes/" + is.getUnlocalizedName();
    }
    
    static String getDefaultHyperlink(ItemStack[] items) {
        if (items == null || items.length == 0) return null;
        if (items.length == 1) return getDefaultHyperlink(items[0]);
        return null;
    }

    void cleanWildlings() {
        if (is != null && is.getItem() == null) {
            is = null;
        }
        if (ItemUtil.isWildcard(is, false)) {
            List<ItemStack> out = ItemUtil.getSubItems(is);
            entries = out.toArray(new ItemStack[out.size()]); // If you give me a wildcard here, then it's your own damn fault if that causes a crash
            if (entries.length == 0) {
                is = is.copy();
                is.setItemDamage(0);
            } else {
                is = null;
            }
        } else if (entries != null) {
            // Probably an OD list, which may contain wildcards, which will have to be expanded
            List<ItemStack> wildingChildren = null;
            for (ItemStack wildling : entries) {
                if (wildling == null || wildling.getItem() == null) {
                    continue;
                }
                if (!ItemUtil.isWildcard(wildling, false)) continue;
                if (wildingChildren == null) {
                    wildingChildren = ItemUtil.getSubItems(wildling);
                } else {
                    wildingChildren.addAll(ItemUtil.getSubItems(wildling));
                }
            }
            if (wildingChildren != null && !wildingChildren.isEmpty()) {
                for (ItemStack nonWild : entries) {
                    if (nonWild == null || nonWild.getItem() == null) {
                        continue;
                    }
                    if (!ItemUtil.isWildcard(nonWild, true)) wildingChildren.add(nonWild);
                }
                for (Iterator<ItemStack> iterator = wildingChildren.iterator(); iterator.hasNext(); ) {
                    ItemStack is = iterator.next();
                    if (is == null || is.getItem() == null) iterator.remove();
                }
                entries = wildingChildren.toArray(new ItemStack[wildingChildren.size()]);
            }
        }
    }

    @Override
    public String toString() {
        return is + " ==> " + getLink();
    }
    
    @Override
    public int getWidth(FontRenderer font) {
        return 16;
    }
    
    @Override
    public int getPaddingAbove() {
        return (16 - WordPage.TEXT_HEIGHT) / 2;
    }
    
    @Override
    public int getWordHeight() {
        return WordPage.TEXT_HEIGHT + getPaddingAbove();
    }

    static int active_index;
    public ItemStack getItem() {
        active_index = 0;
        if (is != null) return is;
        if (entries == null || entries.length == 0) return null;
        long now = System.currentTimeMillis() / 1000;
        now %= entries.length;
        active_index = (int) now;
        return entries[active_index];
    }

    void itemErrored() {
        if (is != null) {
            is = null;
        }
        if (entries != null && active_index < entries.length) {
            entries[active_index] = null;
        }
    }

    @Override
    public int draw(int x, int y, boolean hover, FontRenderer font) {
        ItemStack toDraw = getItem();
        if (toDraw == null) return 16;
        y -= 4;
        
        {
            GlStateManager.disableTexture2D();
            float gray = DocViewer.dark() ? 0.2F : 139F/0xFF;
            GlStateManager.color(gray, gray, gray);

            float z = 0;
            float d = 16;

            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex3f(x + 0, y + 0, z);
            GL11.glVertex3f(x + 0, y + d, z);
            GL11.glVertex3f(x + d, y + d, z);
            GL11.glVertex3f(x + d, y + 0, z);
            GL11.glEnd();
            
            if (hover) {
                int color = getLinkColor(hover);
                byte r = (byte) ((color >> 16) & 0xFF);
                byte g = (byte) ((color >> 8) & 0xFF);
                byte b = (byte) ((color >> 0) & 0xFF);
                GlStateManager.color(r / 255F, g / 255F, b / 255F);
                GL11.glLineWidth(1);
                
                GL11.glBegin(GL11.GL_LINE_LOOP);
                GL11.glVertex3f(x + 0, y + 0, z);
                GL11.glVertex3f(x + 0, y + d, z);
                GL11.glVertex3f(x + d, y + d, z);
                GL11.glVertex3f(x + d, y + 0, z);
                GL11.glEnd();
            }
            
            GlStateManager.color(1, 1, 1);
            GlStateManager.enableTexture2D();
        }
        
        try {
            DocViewer.drawItem(toDraw, x, y, font);
        } catch (Throwable t) {
            t.printStackTrace();
            itemErrored();
            try {
                // Try to finish drawing the item.
                Tessellator.getInstance().draw();
            } catch (IllegalStateException e) {
                // Ignore it.
            }
        }
        return 16;
    }
    
    @Override
    public void drawHover(int mouseX, int mouseY) {
        ItemStack toDraw = getItem();
        if (toDraw == null) return;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            DocViewer.drawItemTip(toDraw, mouseX, mouseY);
        } catch (Throwable t) {
            t.printStackTrace();
            itemErrored();
        }
        GL11.glPopAttrib();
    }

    @Override
    public void writeHtml(IHtmlTypesetter out) {
        if (entries != null) {
            out.write(entries);
        } else {
            out.write(is);
        }
    }
}
