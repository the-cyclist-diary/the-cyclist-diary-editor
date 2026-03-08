# Polyline Generator CLI

CLI pour générer des polylines encodées à partir de fichiers GPX.

### Utilisation
il faut bien spécifier le nom du QuarkusMain permettant de builder la CLI et non pas l'Action GitHub.

```bash
# 1. Compiler l'application
mvn package -Dquarkus.package.main-class=polyline-cli

# 2. Exécuter la CLI
java -jar target/quarkus-app/quarkus-run.jar generate-polylines [options]
```

## 📋 Commandes

### `generate-polylines`

Génère des fichiers JSON de polylines encodées à partir des fichiers GPX.

**Options :**

- `--path, -p <chemin>` : Chemin vers le dossier content contenant les GPX (défaut: `./content`)
- `--full-scan, -f` : Traiter TOUS les fichiers GPX, pas seulement les modifiés
- `--git-repo, -g <chemin>` : Chemin vers la racine du dépôt git (défaut: répertoire courant)
- `--help, -h` : Afficher l'aide

## 🎯 Exemples d'utilisation

### 1. Test rapide en développement

```bash
java -jar target/quarkus-app/quarkus-run.jar generate-polylines
```

### 2. Régénération complète du site

```bash
# Régénère TOUTES les polylines
java -jar target/quarkus-app/quarkus-run.jar generate-polylines --full-scan
```

### 3. Test sur un autre projet

```bash
# Teste la génération sur un autre dossier
java -jar target/quarkus-app/quarkus-run.jar generate-polylines --path ../mon-autre-blog/content --full-scan
```