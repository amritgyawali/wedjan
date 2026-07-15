import type { ImagePickerAsset } from "expo-image-picker";
import { api } from "./api";

export async function uploadPickedAsset(asset: ImagePickerAsset, kind: "IMAGE" | "VIDEO" = "IMAGE") {
  const blob = await (await fetch(asset.uri)).blob();
  const mime = asset.mimeType ?? (kind === "IMAGE" ? "image/jpeg" : "video/mp4");
  const presign = await api.POST("/api/v1/media/presign", { body: { kind, mime, bytes: blob.size } });
  if (!presign.data) throw new Error(presign.error?.error.message ?? "Could not prepare upload");
  const uploaded = await fetch(presign.data.uploadUrl, { method: "PUT", headers: presign.data.headers, body: blob });
  if (!uploaded.ok) throw new Error("Upload failed");
  const complete = await api.POST("/api/v1/media/{mediaId}/complete", { params: { path: { mediaId: presign.data.mediaId } } });
  if (!complete.data) throw new Error(complete.error?.error.message ?? "Could not finish upload");
  return complete.data;
}
