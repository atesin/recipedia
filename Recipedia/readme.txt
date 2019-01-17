
PUBLICAR DESPUES QUE ESTE TODO LISTO

- release notes
- subir codigo a github, crear repos (recipedia, debug)


TODO

- revisa los permisos
- todo documentado como debe ser en plugin.yml
- qa

-------------------------------

+ agregar diccionario generico (material names)
+ arregla el sistema de ayuda
+ implementar la funcion "target", como lo habia hecho o con AxisAlignedBB (busca "hitbox" en los foros y youtube)
+ implementar archivos de idioma
+ implementar localizacion
+ permisos

-------------------------------

- sigue el comando -hand- hasta la clase Session cuando llama a InventoryKeeper
- completa las clases


InventoryClickEvent

haciendo clic en items llama a 4 metodos

- quickbar                      -> showPage       > inv.updateStorage
- non-left (recetas inversas)   -> updateProducts > updateProductsPrivate > showPage > inv.updateHotbar -> inv.updateCrafting
- en container + mirando receta -> showRecipe     > inv.updateCrafting
- otro                          -> updateRecipes  > updateProductsPrivate > ...


prueba programar eventos despues de InventoryClickEvent, las firmas de metodos llamados son

- session.reset();
- closeSession(e.getWhoClicked());
- session.showPage(e.getSlot());
- session.updateProducts(recipes.getInverseRecipeProducts(item));
- session.showRecipe(item, e.getSlot());
- session.updateRecipes(item);

podria ser...

dispatch(action, player, slotNum, inverseRecipesList, item)

actions:

- dispatch("resetSession", null, 0, null, null)
- dispatch("closeSession", player, 0, null, null)
- dispatch("showPage", null, N, null, null)
- dispatch("updateProducts", null, 0, itemList, null)
- dispatch("showRecipe" , null, 






