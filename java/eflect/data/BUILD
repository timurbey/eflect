load("@rules_java//java:defs.bzl", "java_library")

java_library(
  name = "data",
  visibility = ["//visibility:public"],
  srcs = glob(["*.java"]),
  deps = [
    "//java/eflect/util",
    "@clerk//java/clerk",
  ]
)
