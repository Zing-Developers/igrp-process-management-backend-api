# IGRP Process Management Backend Dockerfile

## 1. Ambiente Dev Zing

## 1.1 Base Images

Foram utilizadas as seguintes imagens base, de forma a ter acesso ao bash dentro dos containers, uma vez que um dos requisitos ao correto funcionamento
no ambiente DEV da IRN, a injecção de secrets atravez do agente de Hashicorp Vault, injecção essa que é feita atravez de comando bash:

maven:3.9.9-eclipse-temurin-23 AS build
eclipse-temurin:23-jre


## 1. Ambiente Dev IRN

## 1.1 Base Images

No ambiente DEV da IRN, uma vez que são utilizadas imagens internas que incluem já a instalação dos devidos certificados, não é utilizada nenhuma secção de instalação de certificados no Dockerfile do mesmo, as imagens utilizadas são as seguintes:

FROM docker.tools.irn.internal/base/java-sdk:1.0.0 AS build
FROM docker.tools.irn.internal/base/java-jre:1.0.0


## 1.2 Preservação de Dockerfile nos ambientes da IRN

Importa referir que será sempre necessário ter um elevado grau de atenção ao efetuar Merge Requests de código que vem diretamente dos repositórios da Zing, para que prevaleça sempre o Dockerfile customizado da IRN



