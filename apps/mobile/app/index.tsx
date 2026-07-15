import { Redirect } from "expo-router";
import { ActivityIndicator, View } from "react-native";
import { tokens } from "@wedjan/ui-tokens";
import { useAuth } from "@/lib/auth-context";

export default function Index() {
  const { status } = useAuth();

  if (status === "loading") {
    return (
      <View
        style={{
          flex: 1,
          alignItems: "center",
          justifyContent: "center",
          backgroundColor: tokens.colors.background,
        }}
      >
        <ActivityIndicator color={tokens.colors.brandPink} />
      </View>
    );
  }

  return status === "authenticated" ? (
    <Redirect href="/(app)/home" />
  ) : (
    <Redirect href="/onboarding" />
  );
}
