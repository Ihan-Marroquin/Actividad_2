public class PotionBrewer {
    // Ingredient costs in gold coins
    private static final double HERB_PRICE = 5.50;
    private static final int MUSHROOM_PRICE = 3;
    private String brewerName;
    private double goldCoins;
    private int potionsBrewed;

    public PotionBrewer(String name, double startingGold) {
        this.brewerName = name;
        this.goldCoins = startingGold;
        this.potionsBrewed = 0;
    }

public static void main(String[] args) {
    PotionBrewer wizard = new PotionBrewer("Gandalf, the Wise", 100.0);
    String[] ingredients = {"Mandrake Root", "Dragon Scale", "Phoenix Feather"};

    wizard.brewHealthPotion(3, 2); // 3 herbs, 2 mushrooms
    wizard.brewHealthPotion(5, 4);

    wizard.printStatus();
}

 /* Brews a potion if we have enough gold */
 public void brewHealthPotion(int herbCount, int mushroomCount) {
 double totalCost = (herbCount * HERB_PRICE) + (mushroomCount * MUSHROOM_PRICE
);
 if (totalCost <= this.goldCoins) {
 this.goldCoins -= totalCost; // Deduct the cost
 this.potionsBrewed++;
 System.out.println("Success! Potion brewed for " + totalCost + " gold.");
 } else {
 System.out.println("Not enough gold! Need: " + totalCost);
 }
 }
 // Prints the current brewer status
 public void printStatus() {
 System.out.println("\n=== Brewer Status ===");
 System.out.println("Name: " + this.brewerName);
 System.out.println("Gold remaining: " + this.goldCoins);
 System.out.println("Potions brewed: " + this.potionsBrewed);
 }
 }