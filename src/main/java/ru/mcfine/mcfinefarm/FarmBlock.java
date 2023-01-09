package ru.mcfine.mcfinefarm;

import org.bukkit.Material;

public class FarmBlock {
    private Material material;
    private double payout;

    public FarmBlock(Material material, double doub){
        this.material = material;
        payout = doub;
    }

    public Material getMaterial() {
        return material;
    }

    public void setMaterial(Material material) {
        this.material = material;
    }

    public double getPayout() {
        return payout;
    }

    public void setPayout(double payout) {
        this.payout = payout;
    }
}
