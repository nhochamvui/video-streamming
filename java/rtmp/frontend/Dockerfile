# syntax=docker/dockerfile:1

FROM node:20-alpine AS builder
WORKDIR /build/frontend
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY . .
RUN npm run build

FROM alpine:3.20
COPY --from=builder /build/frontend/dist /dist