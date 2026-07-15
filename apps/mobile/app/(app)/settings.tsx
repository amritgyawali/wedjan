import { router } from "expo-router";
import { Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import { tokens } from "@wedjan/ui-tokens";
import type { Role } from "@wedjan/shared";
import { useAuth } from "@/lib/auth-context";

const ROLE_LABEL: Record<string, string> = {
  CUSTOMER: "Planning events",
  VENDOR: "Vendor",
  FREELANCER: "Freelancer",
  ADMIN: "Admin",
};

export default function SettingsScreen() {
  const { me, activeRole, setActiveRole, logout } = useAuth();
  if (!me) return null;

  return (
    <ScrollView style={styles.screen} contentContainerStyle={{ padding: 24, paddingTop: 72 }}>
      <Text style={styles.title}>Settings</Text>

      <View style={styles.card}>
        <Text style={styles.cardLabel}>Account</Text>
        <Text style={styles.value}>{me.account.email}</Text>
        <Text style={styles.muted}>
          {me.profile.city ? `${me.profile.city} · ` : ""}
          {me.account.defaultCurrency}
        </Text>
      </View>

      {me.roles.length > 1 && (
        <View style={styles.card}>
          <Text style={styles.cardLabel}>Viewing as</Text>
          <View style={styles.roleRow}>
            {me.roles.map((role) => (
              <Pressable
                key={role}
                style={[styles.roleChip, role === activeRole && styles.roleChipActive]}
                onPress={() => setActiveRole(role as Role)}
              >
                <Text
                  style={[
                    styles.roleChipText,
                    role === activeRole && styles.roleChipTextActive,
                  ]}
                >
                  {ROLE_LABEL[role] ?? role}
                </Text>
              </Pressable>
            ))}
          </View>
        </View>
      )}

      <Pressable
        style={styles.logoutButton}
        onPress={async () => {
          await logout();
          router.replace("/(auth)/login");
        }}
      >
        <Text style={styles.logoutText}>Log out</Text>
      </Pressable>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: tokens.colors.background,
  },
  title: {
    color: tokens.colors.onSurface,
    fontSize: 28,
    fontWeight: "700",
    marginBottom: 24,
  },
  card: {
    borderWidth: 1,
    borderColor: "rgba(226, 226, 226, 0.8)",
    borderRadius: tokens.radii.xl,
    padding: 20,
    backgroundColor: tokens.colors.surface,
    marginBottom: 16,
    gap: 4,
  },
  cardLabel: {
    color: tokens.colors.textMuted,
    fontSize: 11,
    fontWeight: "700",
    letterSpacing: 1,
    textTransform: "uppercase",
    marginBottom: 6,
  },
  value: {
    color: tokens.colors.onSurface,
    fontSize: 16,
    fontWeight: "600",
  },
  muted: {
    color: tokens.colors.secondary,
    fontSize: 13,
  },
  roleRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  roleChip: {
    borderWidth: 1.5,
    borderColor: tokens.colors.surfaceVariant,
    borderRadius: tokens.radii.pill,
    paddingHorizontal: 16,
    paddingVertical: 8,
  },
  roleChipActive: {
    borderColor: tokens.colors.brandPink,
    backgroundColor: tokens.colors.pinkSoft,
  },
  roleChipText: {
    color: tokens.colors.secondary,
    fontSize: 13,
    fontWeight: "600",
  },
  roleChipTextActive: {
    color: tokens.colors.brandPink,
  },
  logoutButton: {
    minHeight: 48,
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 1.5,
    borderColor: tokens.colors.danger,
    borderRadius: tokens.radii.pill,
    marginTop: 8,
  },
  logoutText: {
    color: tokens.colors.danger,
    fontSize: 14,
    fontWeight: "700",
  },
});
