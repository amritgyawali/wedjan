import { router, useLocalSearchParams } from "expo-router";
import { useState } from "react";
import { Pressable, ScrollView, Text, TextInput } from "react-native";
import { apiErrorMessage } from "@wedjan/shared";
import { api } from "@/lib/api";
import { formStyles as s } from "@/lib/form-styles";

export default function VerifyScreen() {
  const params = useLocalSearchParams<{ email?: string }>();
  const [email, setEmail] = useState(params.email ?? "");
  const [code, setCode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit() {
    setError(null);
    setSubmitting(true);
    try {
      const { response, error: body } = await api.POST("/api/v1/auth/verify-email", {
        body: { email: email.trim(), code },
      });
      if (!response.ok) {
        setError(apiErrorMessage(body, "Verification failed — check the code"));
        return;
      }
      router.replace("/(auth)/login");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <ScrollView style={s.screen} keyboardShouldPersistTaps="handled">
      <Text style={s.wordmark}>wedjan</Text>
      <Text style={s.title}>Check your email</Text>
      <Text style={s.subtitle}>
        We sent a 6-digit code to {email || "your inbox"}. Enter it below.
      </Text>

      {!params.email && (
        <>
          <Text style={s.label}>Email</Text>
          <TextInput
            style={s.input}
            autoCapitalize="none"
            keyboardType="email-address"
            value={email}
            onChangeText={setEmail}
          />
        </>
      )}
      <Text style={s.label}>Verification code</Text>
      <TextInput
        style={[s.input, s.otpInput]}
        keyboardType="number-pad"
        maxLength={6}
        value={code}
        onChangeText={(value) => setCode(value.replace(/\D/g, ""))}
      />
      {error && <Text style={s.error}>{error}</Text>}
      <Pressable
        style={[s.primaryButton, submitting && s.primaryButtonDisabled]}
        disabled={submitting}
        onPress={onSubmit}
      >
        <Text style={s.primaryButtonText}>{submitting ? "Verifying…" : "Verify email"}</Text>
      </Pressable>
    </ScrollView>
  );
}
