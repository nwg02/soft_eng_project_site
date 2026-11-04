package edu.gsu.gisu;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Shared curated 2027 issuer-document examples. Not an exhaustive inventory
 * of Georgia plans, ZIP-level eligibility, personal rates, or an enrollment feed.
 * Keep these entries sourced from the official documents already published on /plans.
 */
public final class GeorgiaPlanCatalog {
    private GeorgiaPlanCatalog() {}

    public record Plan(String slug, String issuer, String metal, String type,
                       int deductibleInName, String name, String documentUrl,
                       String shopUrl, String documentLabel) {
        public String getSlug() { return slug; }
        public String getIssuer() { return issuer; }
        public String getMetal() { return metal; }
        public String getType() { return type; }
        public int getDeductibleInName() { return deductibleInName; }
        public String getName() { return name; }
        public String getDocumentUrl() { return documentUrl; }
        public String getShopUrl() { return shopUrl; }
        public String getDocumentLabel() { return documentLabel; }
        public String getDeductibleLabel() {
            return "$" + String.format(Locale.US, "%,d", deductibleInName);
        }
    }

    private static final List<Plan> PLANS = List.of(
            new Plan("gisu-plan-01", "Alliant Health Plans", "Gold", "PPO", 1500,
                    "SoloCare Gold PPO $1500 $10 Chiro", "https://alliantplans.com/docs/solocare/2027/GA0040002-01.pdf",
                    "https://enroll.alliantplans.com/", "2027 Summary of Benefits & Coverage"),
            new Plan("gisu-plan-02", "Alliant Health Plans", "Silver", "PPO", 6500,
                    "SoloCare Silver PPO $6500 $25 Chiro", "https://alliantplans.com/docs/solocare/2027/GA0040017-01.pdf",
                    "https://enroll.alliantplans.com/", "2027 Summary of Benefits & Coverage"),
            new Plan("gisu-plan-03", "Alliant Health Plans", "Silver", "HMO", 5000,
                    "SoloCare Silver HMO $5000 $25", "https://alliantplans.com/docs/solocare/2027/GA0110009-01.pdf",
                    "https://enroll.alliantplans.com/", "2027 Summary of Benefits & Coverage"),
            new Plan("gisu-plan-04", "Alliant Health Plans", "Gold", "HMO", 500,
                    "SoloCare Gold HMO $500 $15", "https://alliantplans.com/docs/solocare/2027/GA0110052-01.pdf",
                    "https://enroll.alliantplans.com/", "2027 Summary of Benefits & Coverage"),
            new Plan("gisu-plan-05", "Alliant Health Plans", "Bronze", "HMO", 7800,
                    "SoloCare HSA Bronze HMO $7800 0% After DED", "https://alliantplans.com/docs/solocare/2027/GA0110019-01.pdf",
                    "https://enroll.alliantplans.com/", "2027 Summary of Benefits & Coverage"),
            new Plan("gisu-plan-06", "Kaiser Permanente", "Gold", "HMO", 500,
                    "Gold HMO $500 $25", "https://healthy.kaiserpermanente.org/content/dam/kporg/final/documents/health-plan-documents/eoc/ga/individual-family/2027/on-gold-500-25-ga-en.pdf",
                    "https://healthy.kaiserpermanente.org/georgia/shop-plans", "2027 Evidence of Coverage"),
            new Plan("gisu-plan-07", "Kaiser Permanente", "Gold", "HMO", 1000,
                    "Gold HMO $1000 $25", "https://healthy.kaiserpermanente.org/content/dam/kporg/final/documents/health-plan-documents/eoc/ga/individual-family/2027/on-gold-1000-25-ga-en.pdf",
                    "https://healthy.kaiserpermanente.org/georgia/shop-plans", "2027 Evidence of Coverage"),
            new Plan("gisu-plan-08", "Kaiser Permanente", "Silver", "HMO", 3000,
                    "Silver HMO $3000 $40", "https://healthy.kaiserpermanente.org/content/dam/kporg/final/documents/health-plan-documents/eoc/ga/individual-family/2027/on-silver-3000-40-ga-en.pdf",
                    "https://healthy.kaiserpermanente.org/georgia/shop-plans", "2027 Evidence of Coverage"),
            new Plan("gisu-plan-09", "Kaiser Permanente", "Silver", "HMO", 3300,
                    "Silver HMO $3300 $30", "https://healthy.kaiserpermanente.org/content/dam/kporg/final/documents/health-plan-documents/eoc/ga/individual-family/2027/on-silver-3300-30-ga-en.pdf",
                    "https://healthy.kaiserpermanente.org/georgia/shop-plans", "2027 Evidence of Coverage"),
            new Plan("gisu-plan-10", "Kaiser Permanente", "Silver", "HMO", 6000,
                    "Silver HMO $6000 $50", "https://healthy.kaiserpermanente.org/content/dam/kporg/final/documents/health-plan-documents/eoc/ga/individual-family/2027/on-silver-6000-50-ga-en.pdf",
                    "https://healthy.kaiserpermanente.org/georgia/shop-plans", "2027 Evidence of Coverage"),
            new Plan("gisu-plan-11", "Kaiser Permanente", "Bronze", "HMO", 6500,
                    "Bronze HMO $6500 40% HSA", "https://healthy.kaiserpermanente.org/content/dam/kporg/final/documents/health-plan-documents/eoc/ga/individual-family/2027/on-bronze-6500-40-hsa-ga-en.pdf",
                    "https://healthy.kaiserpermanente.org/georgia/shop-plans", "2027 Evidence of Coverage"),
            new Plan("gisu-plan-12", "Kaiser Permanente", "Bronze", "HMO", 7500,
                    "Bronze HMO $7500 $50 HSA", "https://healthy.kaiserpermanente.org/content/dam/kporg/final/documents/health-plan-documents/eoc/ga/individual-family/2027/on-bronze-7500-50-hsa-ga-en.pdf",
                    "https://healthy.kaiserpermanente.org/georgia/shop-plans", "2027 Evidence of Coverage")
    );

    public static List<Plan> all() {
        return PLANS;
    }

    public static boolean validPriority(String priority) {
        return "lower_premium".equals(priority) ||
                "lower_deductible".equals(priority) ||
                "provider_flexibility".equals(priority) ||
                "balanced".equals(priority);
    }

    // Educational ordering by disclosed plan characteristics, not pricing,
    // qualification, suitability, provider network, or verified availability.
    public static List<Plan> suggestions(String priority) {
        if (!validPriority(priority)) {
            throw new IllegalArgumentException("Invalid coverage priority");
        }
        Comparator<Plan> order = Comparator
                .comparingInt((Plan p) -> preferenceScore(priority, p))
                .thenComparingInt(Plan::deductibleInName)
                .thenComparing(Plan::name);
        return PLANS.stream().sorted(order).limit(4).toList();
    }

    private static int preferenceScore(String priority, Plan plan) {
        if ("lower_deductible".equals(priority)) return 0;
        if ("provider_flexibility".equals(priority)) {
            return "PPO".equals(plan.type()) ? 0 : 1;
        }
        if ("lower_premium".equals(priority)) {
            return metalScore(plan.metal(), "Bronze", "Silver", "Gold");
        }
        return metalScore(plan.metal(), "Silver", "Gold", "Bronze");
    }

    private static int metalScore(String metal, String first, String second, String third) {
        if (first.equals(metal)) return 0;
        if (second.equals(metal)) return 1;
        if (third.equals(metal)) return 2;
        return 3;
    }

    public static String explanation(String priority) {
        return switch (priority) {
            case "lower_premium" ->
                    "Bronze options appear first as a starting point for exploring typically lower-premium metal tiers. Actual premiums, savings, and total annual costs are unknown; Bronze is not guaranteed to cost less for you.";
            case "lower_deductible" ->
                    "Options with smaller deductible figures in their marketing names appear first. Those figures are not verified household deductibles or a complete picture of cost sharing. Read each official document.";
            case "provider_flexibility" ->
                    "PPO examples appear first because PPO designs often allow more out-of-network flexibility. Whether a doctor participates, and the network restrictions in your county, must still be checked.";
            case "balanced" ->
                    "Silver options appear first as a general starting point for comparing monthly and out-of-pocket costs. This is not a calculation of value, affordability, eligibility, or cost-sharing reductions.";
            default -> throw new IllegalArgumentException("Unknown priority");
        };
    }
}
