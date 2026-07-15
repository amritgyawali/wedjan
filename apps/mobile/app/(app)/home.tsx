import { ScrollView, StyleSheet, Text, View } from "react-native";
import { tokens } from "@wedjan/ui-tokens";
import { useAuth } from "@/lib/auth-context";

const EMPTY_COPY: Record<string, { title: string; body: string }> = {
  CUSTOMER: {
    title: "No events yet",
    body: "Event planning arrives with the workspace phase — create your event, invite family, and book every vendor with protected payments.",
  },
  VENDOR: {
    title: "Your storefront starts here",
    body: "Vendor onboarding opens in the next phase: build your profile, publish priced packages, and get verified.",
  },
  FREELANCER: {
    title: "No shifts yet",
    body: "The gig layer is coming: browse shifts, see exact take-home pay, get paid within 72 hours.",
  },
  ADMIN: {
    title: "Admin console pending",
    body: "Ops tooling ships in a later phase.",
  },
};

export default function HomeScreen() {
  const { me, activeRole } = useAuth();
  if (!me || !activeRole) return null;

  const copy = EMPTY_COPY[activeRole];
  const name = me.profile.displayName || me.account.email.split("@")[0];

  return (
    <ScrollView style={styles.screen} contentContainerStyle={{ padding: 24, paddingTop: 72 }}>
      <Text style={styles.greeting}>Welcome, {name}</Text>
      <View style={styles.card}>
        <Text style={styles.cardTitle}>{copy.title}</Text>
        <Text style={styles.cardBody}>{copy.body}</Text>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: tokens.colors.background,
  },
  greeting: {
    color: tokens.colors.onSurface,
    fontSize: 28,
    fontWeight: "700",
    marginBottom: 24,
  },
  card: {
    borderWidth: 1.5,
    borderStyle: "dashed",
    borderColor: tokens.colors.surfaceVariant,
    borderRadius: tokens.radii.xl,
    padding: 32,
    backgroundColor: tokens.colors.surface,
    alignItems: "center",
    gap: 8,
  },
  cardTitle: {
    color: tokens.colors.onSurface,
    fontSize: 20,
    fontWeight: "700",
    textAlign: "center",
  },
  cardBody: {
    color: tokens.colors.secondary,
    fontSize: 14,
    lineHeight: 21,
    textAlign: "center",
  },
});
