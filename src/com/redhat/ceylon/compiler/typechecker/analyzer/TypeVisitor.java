package com.redhat.ceylon.compiler.typechecker.analyzer;

import static com.redhat.ceylon.compiler.typechecker.analyzer.Util.declaredInPackage;
import static com.redhat.ceylon.compiler.typechecker.analyzer.Util.getPackageTypeDeclaration;
import static com.redhat.ceylon.compiler.typechecker.analyzer.Util.getTypeArguments;
import static com.redhat.ceylon.compiler.typechecker.analyzer.Util.getTypeDeclaration;
import static com.redhat.ceylon.compiler.typechecker.analyzer.Util.getTypeMember;
import static com.redhat.ceylon.compiler.typechecker.analyzer.Util.getTypedDeclaration;
import static com.redhat.ceylon.compiler.typechecker.analyzer.Util.unwrapExpressionUntilTerm;
import static com.redhat.ceylon.compiler.typechecker.tree.Util.formatPath;
import static com.redhat.ceylon.compiler.typechecker.tree.Util.name;
import static com.redhat.ceylon.model.typechecker.model.SiteVariance.IN;
import static com.redhat.ceylon.model.typechecker.model.SiteVariance.OUT;
import static com.redhat.ceylon.model.typechecker.model.Util.getContainingClassOrInterface;
import static com.redhat.ceylon.model.typechecker.model.Util.intersectionOfSupertypes;
import static com.redhat.ceylon.model.typechecker.model.Util.isNativeImplementation;
import static com.redhat.ceylon.model.typechecker.model.Util.isToplevelAnonymousClass;
import static com.redhat.ceylon.model.typechecker.model.Util.isToplevelClassConstructor;
import static com.redhat.ceylon.model.typechecker.model.Util.isTypeUnknown;
import static com.redhat.ceylon.model.typechecker.model.Util.notOverloaded;
import static com.redhat.ceylon.model.typechecker.model.Util.producedType;
import static java.lang.Integer.parseInt;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.redhat.ceylon.common.Backend;
import com.redhat.ceylon.common.BackendSupport;
import com.redhat.ceylon.compiler.typechecker.context.TypecheckerUnit;
import com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Type;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;
import com.redhat.ceylon.model.cmr.JDKUtils;
import com.redhat.ceylon.model.typechecker.model.Class;
import com.redhat.ceylon.model.typechecker.model.ClassAlias;
import com.redhat.ceylon.model.typechecker.model.ClassOrInterface;
import com.redhat.ceylon.model.typechecker.model.Constructor;
import com.redhat.ceylon.model.typechecker.model.Declaration;
import com.redhat.ceylon.model.typechecker.model.Generic;
import com.redhat.ceylon.model.typechecker.model.Import;
import com.redhat.ceylon.model.typechecker.model.ImportList;
import com.redhat.ceylon.model.typechecker.model.Interface;
import com.redhat.ceylon.model.typechecker.model.IntersectionType;
import com.redhat.ceylon.model.typechecker.model.Method;
import com.redhat.ceylon.model.typechecker.model.MethodOrValue;
import com.redhat.ceylon.model.typechecker.model.Module;
import com.redhat.ceylon.model.typechecker.model.ModuleImport;
import com.redhat.ceylon.model.typechecker.model.Package;
import com.redhat.ceylon.model.typechecker.model.Parameter;
import com.redhat.ceylon.model.typechecker.model.ProducedType;
import com.redhat.ceylon.model.typechecker.model.Scope;
import com.redhat.ceylon.model.typechecker.model.Specification;
import com.redhat.ceylon.model.typechecker.model.TypeAlias;
import com.redhat.ceylon.model.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.model.typechecker.model.TypeParameter;
import com.redhat.ceylon.model.typechecker.model.TypedDeclaration;
import com.redhat.ceylon.model.typechecker.model.UnionType;
import com.redhat.ceylon.model.typechecker.model.Unit;
import com.redhat.ceylon.model.typechecker.model.UnknownType;
import com.redhat.ceylon.model.typechecker.model.Value;

/**
 * Second phase of type analysis.
 * Scan the compilation unit looking for literal type 
 * declarations and maps them to the associated model 
 * objects. Also builds up a list of imports for the 
 * compilation unit. Finally, assigns types to the 
 * associated model objects of declarations declared 
 * using an explicit type (this must be done in
 * this phase, since shared declarations may be used
 * out of order in expressions).
 * 
 * @author Gavin King
 *
 */
public class TypeVisitor extends Visitor {
    
    private TypecheckerUnit unit;
    private final BackendSupport backendSupport;

    private boolean inDelegatedConstructor;
    private boolean inTypeLiteral;
    private boolean inExtendsOrClassAlias;
    
    public TypeVisitor(BackendSupport backendSupport) {
        this.backendSupport = backendSupport;
    }
    
    public TypeVisitor(TypecheckerUnit unit, BackendSupport backendSupport) {
        this.unit = unit;
        this.backendSupport = backendSupport;
    }
    
    @Override public void visit(Tree.CompilationUnit that) {
        unit = that.getUnit();
        super.visit(that);
        HashSet<String> set = new HashSet<String>();
        for (Tree.Import im: that.getImportList().getImports()) {
            Tree.ImportPath ip = im.getImportPath();
            if (ip!=null) {
                String mp = formatPath(ip.getIdentifiers());
                if (!set.add(mp)) {
                    ip.addError("duplicate import: '" + mp + "'");
                }
            }
        }
    }
    
    @Override
    public void visit(Tree.Import that) {
        Package importedPackage = 
                getPackage(that.getImportPath(), backendSupport);
        if (importedPackage!=null) {
            that.getImportPath().setModel(importedPackage);
            Tree.ImportMemberOrTypeList imtl = 
                    that.getImportMemberOrTypeList();
            if (imtl!=null) {
                ImportList il = imtl.getImportList();
                il.setImportedScope(importedPackage);
                Set<String> names = new HashSet<String>();
                for (Tree.ImportMemberOrType member: 
                        imtl.getImportMemberOrTypes()) {
                    names.add(importMember(member, importedPackage, il));
                }
                if (imtl.getImportWildcard()!=null) {
                    importAllMembers(importedPackage, names, il);
                } 
                else if (imtl.getImportMemberOrTypes().isEmpty()) {
                    imtl.addError("empty import list");
                }
            }
        }
    }
    
    private void importAllMembers(Package importedPackage, 
            Set<String> ignoredMembers, ImportList il) {
        for (Declaration dec: importedPackage.getMembers()) {
            if (dec.isShared() && 
                    !dec.isAnonymous() && 
                    !ignoredMembers.contains(dec.getName()) &&
                    !isNonimportable(importedPackage, dec.getName())) {
                addWildcardImport(il, dec);
            }
        }
    }
    
    private void importAllMembers(TypeDeclaration importedType, 
            Set<String> ignoredMembers, ImportList til) {
        for (Declaration dec: importedType.getMembers()) {
            if (dec.isShared() && 
                    (dec.isStaticallyImportable() || 
                            dec instanceof Constructor) && 
                    !dec.isAnonymous() && 
                    !ignoredMembers.contains(dec.getName())) {
                addWildcardImport(til, dec, importedType);
            }
        }
    }
    
    private void addWildcardImport(ImportList il, Declaration dec) {
        if (!hidesToplevel(dec)) {
            Import i = new Import();
            i.setAlias(dec.getName());
            i.setDeclaration(dec);
            i.setWildcardImport(true);
            addWildcardImport(il, dec, i);
        }
    }
    
    private void addWildcardImport(ImportList il, Declaration dec, TypeDeclaration td) {
        if (!hidesToplevel(dec)) {
            Import i = new Import();
            i.setAlias(dec.getName());
            i.setDeclaration(dec);
            i.setWildcardImport(true);
            i.setTypeDeclaration(td);
            addWildcardImport(il, dec, i);
        }
    }
    
    private void addWildcardImport(ImportList il, Declaration dec, Import i) {
        if (notOverloaded(dec)) {
            String alias = i.getAlias();
            if (alias!=null) {
                Import o = unit.getImport(dec.getName());
                if (o!=null && o.isWildcardImport()) {
                    if (o.getDeclaration().equals(dec)) {
                        //this case only happens in the IDE,
                        //due to reuse of the Unit
                        unit.getImports().remove(o);
                        il.getImports().remove(o);
                    }
                    else {
                        i.setAmbiguous(true);
                        o.setAmbiguous(true);
                    }
                }
                unit.getImports().add(i);
                il.getImports().add(i);
            }
        }
    }
    
    public static Module getModule(Tree.ImportPath path) {
        if (path!=null && 
                !path.getIdentifiers().isEmpty()) {
            String nameToImport = 
                    formatPath(path.getIdentifiers());
            Module module = 
                    path.getUnit().getPackage()
                        .getModule();
            Package pkg = module.getPackage(nameToImport);
            if (pkg != null) {
                Module mod = pkg.getModule();
                if (!pkg.getNameAsString()
                        .equals(mod.getNameAsString())) {
                    path.addError("not a module: '" + 
                            nameToImport + "'");
                    return null;
                }
                if (mod.equals(module)) {
                    return mod;
                }
                //check that the package really does belong to
                //an imported module, to work around bug where
                //default package thinks it can see stuff in
                //all modules in the same source dir
                Set<Module> visited = new HashSet<Module>();
                for (ModuleImport mi: module.getImports()) {
                    if (findModuleInTransitiveImports(mi.getModule(), 
                            mod, visited)) {
                        return mod; 
                    }
                }
            }
            path.addError("module not found in imported modules: '" + 
                    nameToImport + "'", 7000);
        }
        return null;
    }
    
    public static Package getPackage(Tree.ImportPath path, 
            BackendSupport backendSupport) {
        if (path!=null && 
                !path.getIdentifiers().isEmpty()) {
            String nameToImport = 
                    formatPath(path.getIdentifiers());
            Module module = 
                    path.getUnit().getPackage()
                        .getModule();
            Package pkg = module.getPackage(nameToImport);
            if (pkg != null) {
                if (pkg.getModule().equals(module)) {
                    return pkg;
                }
                if (!pkg.isShared()) {
                    path.addError("imported package is not shared: '" + 
                            nameToImport + "'", 402);
                }
//                if (module.isDefault() && 
//                        !pkg.getModule().isDefault() &&
//                        !pkg.getModule().getNameAsString()
//                            .equals(Module.LANGUAGE_MODULE_NAME)) {
//                    path.addError("package belongs to a module and may not be imported by default module: " +
//                            nameToImport);
//                }
                //check that the package really does belong to
                //an imported module, to work around bug where
                //default package thinks it can see stuff in
                //all modules in the same source dir
                Set<Module> visited = new HashSet<Module>();
                for (ModuleImport mi: module.getImports()) {
                    if (findModuleInTransitiveImports(
                            mi.getModule(), pkg.getModule(), 
                            visited)) {
                        return pkg; 
                    }
                }
            } else {
                for (ModuleImport mi: module.getImports()) {
                    if (mi.isNative()) {
                        Backend backend = 
                                Backend.fromAnnotation(mi.getNative());
                        String name = mi.getModule().getNameAsString();
                        if (!backendSupport.supportsBackend(backend)
                                && nameToImport.startsWith(name)) {
                            return null;
                        }
                        if (!backendSupport.supportsBackend(Backend.Java) && 
                                (JDKUtils.isJDKAnyPackage(nameToImport) || 
                                 JDKUtils.isOracleJDKAnyPackage(nameToImport))) {
                            return null;
                        }
                    }
                }
            }
            String help;
            if(module.isDefault())
                help = " (define a module and add module import to its module descriptor)";
            else
                help = " (add module import to module descriptor of '" +
                        module.getNameAsString() + "')";
            path.addError("package not found in imported modules: '" + 
                    nameToImport + "'" + help, 7000);
        }
        return null;
    }
    
    private static boolean findModuleInTransitiveImports(Module moduleToVisit, 
            Module moduleToFind, Set<Module> visited) {
        if (!visited.add(moduleToVisit)) {
            return false;
        }
        else if (moduleToVisit.equals(moduleToFind)) {
            return true;
        }
        else {
            for (ModuleImport imp: moduleToVisit.getImports()) {
                // skip non-exported modules
                if (imp.isExport() &&
                        findModuleInTransitiveImports(imp.getModule(), 
                                moduleToFind, visited)) {
                    return true;
                }
            }
            return false;
        }
    }
    
    private boolean hidesToplevel(Declaration dec) {
        for (Declaration d: unit.getDeclarations()) {
            String n = d.getName();
            if (d.isToplevel() && n!=null && 
                    dec.getName().equals(n)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean checkForHiddenToplevel(Tree.Identifier id, 
            Import i, Tree.Alias alias) {
        for (Declaration d: unit.getDeclarations()) {
            String n = d.getName();
            Declaration idec = i.getDeclaration();
            if (d.isToplevel() && n!=null && 
                    i.getAlias().equals(n) &&
                    !idec.equals(d) && 
                    //it is legal to import an object declaration 
                    //in the current package without providing an
                    //alias:
                    !isLegalAliasFreeImport(d, idec)) {
                if (alias==null) {
                    id.addError("toplevel declaration with this name declared in this unit: '" + n + "'");
                }
                else {
                    alias.addError("toplevel declaration with this name declared in this unit: '" + n + "'");
                }
                return true;
            }
        }
        return false;
    }

    private static boolean isLegalAliasFreeImport
            (Declaration dec, Declaration importedDec) {
        if (importedDec instanceof Value) {
            Value value = (Value) importedDec;
            TypeDeclaration td = value.getTypeDeclaration();
            return td.isAnonymous() && td.equals(dec);
        }
        else {
            return false;
        }
    }
    
    private void importMembers(Tree.ImportMemberOrType member, 
            Declaration d) {
        Tree.ImportMemberOrTypeList imtl = 
                member.getImportMemberOrTypeList();
        if (imtl!=null) {
            if (d instanceof Value) {
                Value v = (Value) d;
                TypeDeclaration td = v.getTypeDeclaration();
                if (td.isAnonymous()) {
                    d = td;
                }
            }
            if (d instanceof TypeDeclaration) {
                Set<String> names = new HashSet<String>();
                ImportList til = imtl.getImportList();
                TypeDeclaration td = (TypeDeclaration) d;
                til.setImportedScope(td);
                List<Tree.ImportMemberOrType> imts = 
                        imtl.getImportMemberOrTypes();
                for (Tree.ImportMemberOrType imt: imts) {
                    names.add(importMember(imt, td, til));
                }
                if (imtl.getImportWildcard()!=null) {
                    importAllMembers(td, names, til);
                }
                else if (imts.isEmpty()) {
                    imtl.addError("empty import list");
                }
            }
            else {
                imtl.addError("member alias list must follow a type");
            }
        }
    }
    
    private void checkAliasCase(Tree.Alias alias, Declaration d) {
        if (alias!=null) {
            Tree.Identifier id = alias.getIdentifier();
            int tt = id.getToken().getType();
            if (d instanceof TypeDeclaration &&
                    tt!=CeylonLexer.UIDENTIFIER) {
                id.addError("imported type should have uppercase alias: '" +
                        d.getName() + "'");
            }
            else if (d instanceof TypedDeclaration &&
                    tt!=CeylonLexer.LIDENTIFIER) {
                id.addError("imported member should have lowercase alias: '" +
                        d.getName() + "'");
            }
        }
    }
    
    private String importMember(Tree.ImportMemberOrType member,
            Package importedPackage, ImportList il) {
        Tree.Identifier id = 
                member.getIdentifier();
        if (id==null) {
            return null;
        }
        Import i = new Import();
        member.setImportModel(i);
        Tree.Alias alias = member.getAlias();
        String name = name(id);
        if (alias==null) {
            i.setAlias(name);
        }
        else {
            i.setAlias(name(alias.getIdentifier()));
        }
        if (isNonimportable(importedPackage, name)) {
            id.addError("root type may not be imported");
            return name;
        }        
        Declaration d = 
                importedPackage.getMember(name, null, false);
        if (d==null) {
            id.addError("imported declaration not found: '" + 
                    name + "'", 
                    100);
            unit.getUnresolvedReferences().add(id);
        }
        else {
            if (!declaredInPackage(d, unit)) {
                if (!d.isShared()) {
                    id.addError("imported declaration is not shared: '" +
                            name + "'", 
                            400);
                }
                else if (d.isPackageVisibility()) {
                    id.addError("imported package private declaration is not visible: '" +
                            name + "'");
                }
                else if (d.isProtectedVisibility()) {
                    id.addError("imported protected declaration is not visible: '" +
                            name + "'");
                }
            }
            i.setDeclaration(d);
            member.setDeclarationModel(d);
            if (il.hasImport(d)) {
                id.addError("already imported: '" + name + "'");
            }
            else if (!checkForHiddenToplevel(id, i, alias)) {
                addImport(member, il, i);
            }
            checkAliasCase(alias, d);
        }
        importMembers(member, d);
        return name;
    }
    
    private String importMember(Tree.ImportMemberOrType member, 
            TypeDeclaration td, ImportList il) {
        Tree.Identifier id = 
                member.getIdentifier();
        if (id==null) {
            return null;
        }
        Import i = new Import();
        member.setImportModel(i);
        Tree.Alias alias = member.getAlias();
        String name = name(id);
        if (alias==null) {
            i.setAlias(name);
        }
        else {
            i.setAlias(name(alias.getIdentifier()));
        }
        Declaration m = td.getMember(name, null, false);
        if (m==null) {
            id.addError("imported declaration not found: '" + 
                    name + "' of '" + 
                    td.getName() + "'", 
                    100);
            unit.getUnresolvedReferences().add(id);
        }
        else {
            List<Declaration> members = 
                    m.getContainer().getMembers();
            for (Declaration d: members) {
                String dn = d.getName();
                if (dn!=null &&
                        dn.equals(name) && 
                        !d.sameKind(m) &&
                        !d.isAnonymous()) {
                    //crazy interop cases like isOpen() + open()
                    id.addError("ambiguous member declaration: '" +
                            name + "' of '" + 
                            td.getName() + "'");
                    return null;
                }
            }
            if (!m.isShared()) {
                id.addError("imported declaration is not shared: '" +
                        name + "' of '" + 
                        td.getName() + "'", 
                        400);
            }
            else if (!declaredInPackage(m, unit)) {
                if (m.isPackageVisibility()) {
                    id.addError("imported package private declaration is not visible: '" +
                            name + "' of '" + 
                            td.getName() + "'");
                }
                else if (m.isProtectedVisibility()) {
                    id.addError("imported protected declaration is not visible: '" +
                            name + "' of '" + 
                            td.getName() + "'");
                }
            }
            i.setTypeDeclaration(td);
            if (!m.isStaticallyImportable() && 
                    !isToplevelClassConstructor(td, m) &&
                    !isToplevelAnonymousClass(m.getContainer())) {
                if (alias==null) {
                    member.addError("does not specify an alias");
                }
            }
            i.setDeclaration(m);
            member.setDeclarationModel(m);
            if (il.hasImport(m)) {
                id.addError("already imported: '" +
                        name + "' of '" + td.getName() + "'");
            }
            else {
                if (m.isStaticallyImportable() ||
                        isToplevelClassConstructor(td, m) ||
                        isToplevelAnonymousClass(m.getContainer())) {
                    if (!checkForHiddenToplevel(id, i, alias)) {
                        addImport(member, il, i);
                    }
                }
                else {
                    addMemberImport(member, il, i);
                }
            }
            checkAliasCase(alias, m);
        }
        importMembers(member, m);
        //imtl.addError("member aliases may not have member aliases");
        return name;
    }

    private void addImport(Tree.ImportMemberOrType member, 
            ImportList il, Import i) {
        String alias = i.getAlias();
        if (alias!=null) {
            Map<String, String> mods = unit.getModifiers();
            if (mods.containsKey(alias) && 
                    mods.get(alias).equals(alias)) {
                //spec says you can't hide a language modifier
                //unless the modifier itself has an alias
                //(this is perhaps a little heavy-handed)
                member.addError("import hides a language modifier: '" + 
                        alias + "'");
            }
            else {
                Import o = unit.getImport(alias);
                if (o==null) {
                    unit.getImports().add(i);
                    il.getImports().add(i);
                }
                else if (o.isWildcardImport()) {
                    unit.getImports().remove(o);
                    il.getImports().remove(o);
                    unit.getImports().add(i);
                    il.getImports().add(i);
                }
                else {
                    member.addError("duplicate import alias: '" + 
                            alias + "'");
                }
            }
        }
    }
    
    private void addMemberImport(Tree.ImportMemberOrType member, 
            ImportList il, Import i) {
        String alias = i.getAlias();
        if (alias!=null) {
            if (il.getImport(alias)==null) {
                unit.getImports().add(i);
                il.getImports().add(i);
            }
            else {
                member.addError("duplicate member import alias: '" + 
                        alias + "'");
            }
        }
    }
    
    private boolean isNonimportable(Package pkg, String name) {
        return pkg.getQualifiedNameString().equals("java.lang") &&
                ("Object".equals(name) ||
                 "Throwable".equals(name) ||
                 "Exception".equals(name));
    }
    
    public void visit(Tree.GroupedType that) {
        super.visit(that);
        Tree.StaticType type = that.getType();
        if (type!=null) {
            that.setTypeModel(type.getTypeModel());
        }
    }
    
    @Override
    public void visit(Tree.UnionType that) {
        super.visit(that);
        List<Tree.StaticType> sts = 
                that.getStaticTypes();
        List<ProducedType> types = 
                new ArrayList<ProducedType>
                    (sts.size());
        for (Tree.StaticType st: sts) {
            //addToUnion( types, st.getTypeModel() );
            ProducedType t = st.getTypeModel();
            if (t!=null) {
                types.add(t);
            }
        }
        UnionType ut = 
                new UnionType(unit);
        ut.setCaseTypes(types);
        ProducedType type = ut.getType();
        that.setTypeModel(type);
        //that.setTarget(pt);
    }
    
    @Override 
    public void visit(Tree.IntersectionType that) {
        super.visit(that);
        List<Tree.StaticType> sts = 
                that.getStaticTypes();
        List<ProducedType> types = 
                new ArrayList<ProducedType>
                    (sts.size());
        for (Tree.StaticType st: sts) {
            //addToIntersection(types, st.getTypeModel(), unit);
            ProducedType t = st.getTypeModel();
            if (t!=null) {
                types.add(t);
            }
        }
        IntersectionType it = 
                new IntersectionType(unit);
        it.setSatisfiedTypes(types);
        ProducedType type = it.getType();
        that.setTypeModel(type);
        //that.setTarget(pt);
    }
    
    @Override 
    public void visit(Tree.SequenceType that) {
        super.visit(that);
        Tree.StaticType elementType = that.getElementType();
        Tree.NaturalLiteral length = that.getLength();
        ProducedType et = elementType.getTypeModel();
        if (et!=null) {
            ProducedType t;
            if (length==null) {
                t = unit.getSequentialType(et);
            }
            else {
                final int len;
                try {
                    len = parseInt(length.getText());
                }
                catch (NumberFormatException nfe) {
                    length.addError("must be a positive decimal integer");
                    return;
                }
                if (len<1) {
                    length.addError("must be positive");
                    return;
                }
                if (len>100) {
                    length.addError("may not be greater than 100");
                    return;
                }
                Class td = unit.getTupleDeclaration();
                t = unit.getType(unit.getEmptyDeclaration());
                for (int i=0; i<len; i++) {
                    t = producedType(td, et, et, t);
                }
            }
            that.setTypeModel(t);
        }
    }
    
    @Override 
    public void visit(Tree.IterableType that) {
        super.visit(that);
        Tree.Type elem = that.getElementType();
        if (elem==null) {
            ProducedType nt = 
                    unit.getNothingDeclaration().getType();
            that.setTypeModel(unit.getIterableType(nt));
            that.addError("iterable type must have an element type");
        }
        else {
            if (elem instanceof Tree.SequencedType) {
                Tree.SequencedType st = 
                        (Tree.SequencedType) elem;
                ProducedType et = 
                        st.getType().getTypeModel();
                if (et!=null) {
                    ProducedType t =
                            st.getAtLeastOne() ?
                                unit.getNonemptyIterableType(et) :
                                unit.getIterableType(et);
                    that.setTypeModel(t);
                }
            }
            else {
                that.addError("malformed iterable type");
            }
        }
    }
    
    @Override
    public void visit(Tree.OptionalType that) {
        super.visit(that);
        List<ProducedType> types = 
                new ArrayList<ProducedType>(2);
        types.add(unit.getType(unit.getNullDeclaration()));
        ProducedType dt = 
                that.getDefiniteType().getTypeModel();
        if (dt!=null) types.add(dt);
        UnionType ut = new UnionType(unit);
        ut.setCaseTypes(types);
        that.setTypeModel(ut.getType());
    }
    
    @Override
    public void visit(Tree.EntryType that) {
        super.visit(that);
        ProducedType kt = 
                that.getKeyType().getTypeModel();
        ProducedType vt = 
                that.getValueType()==null ? 
                        new UnknownType(unit).getType() : 
                        that.getValueType().getTypeModel();
        that.setTypeModel(unit.getEntryType(kt, vt));
    }
    
    @Override
    public void visit(Tree.TypeConstructor that) {
        super.visit(that);
        TypeAlias ta = that.getDeclarationModel();
        ta.setExtendedType(that.getType().getTypeModel());
        ProducedType type = ta.getType();
        type.setTypeConstructor(true);
        that.setTypeModel(type);
    }
    
    @Override
    public void visit(Tree.FunctionType that) {
        super.visit(that);
        Tree.StaticType rt = 
                that.getReturnType();
        if (rt!=null) {
            ProducedType tt = 
                    getTupleType(that.getArgumentTypes(), 
                            unit);
            Interface cd = unit.getCallableDeclaration();
            ProducedType pt = 
                    producedType(cd, rt.getTypeModel(), tt);
            that.setTypeModel(pt);
        }
    }
    
    @Override
    public void visit(Tree.TupleType that) {
        super.visit(that);
        ProducedType tt = 
                getTupleType(that.getElementTypes(), 
                        unit);
        that.setTypeModel(tt);
    }

    static ProducedType getTupleType(List<Tree.Type> ets, Unit unit) {
        List<ProducedType> args = 
                new ArrayList<ProducedType>
                    (ets.size());
        boolean sequenced = false;
        boolean atleastone = false;
        int firstDefaulted = -1;
        for (int i=0; i<ets.size(); i++) {
            Tree.Type st = ets.get(i);
            ProducedType arg = st==null ? 
                    null : st.getTypeModel();
            if (arg==null) {
                arg = new UnknownType(unit).getType();
            }
            else if (st instanceof Tree.SpreadType) {
                //currently we only allow a
                //single spread type, but in
                //future we should also allow
                //X, Y, *Zs
                return st.getTypeModel();
            }
            else if (st instanceof Tree.DefaultedType) {
                if (firstDefaulted==-1) {
                    firstDefaulted = i;
                }
            }
            else if (st instanceof Tree.SequencedType) {
                if (i!=ets.size()-1) {
                    st.addError("variant element must occur last in a tuple type");
                }
                else {
                    sequenced = true;
                    Tree.SequencedType sst = 
                            (Tree.SequencedType) st;
                    atleastone = sst.getAtLeastOne();
                    arg = sst.getType().getTypeModel();
                }
                if (firstDefaulted!=-1 && atleastone) {
                    st.addError("nonempty variadic element must occur after defaulted elements in a tuple type");
                }
            }
            else {
                if (firstDefaulted!=-1) {
                    st.addError("required element must occur after defaulted elements in a tuple type");
                }
            }
            args.add(arg);
        }
        return getTupleType(args, sequenced, 
                atleastone, firstDefaulted, unit);
    }

    //TODO: big copy/paste from Unit.getTupleType(), to eliminate
    //      the canonicalization (since aliases are not yet 
    //      resolvable in this phase)
    public static ProducedType getTupleType(List<ProducedType> elemTypes, 
            boolean variadic, boolean atLeastOne, int firstDefaulted,
            Unit unit) {
        Class td = unit.getTupleDeclaration();
        Interface ed = unit.getEmptyDeclaration();
        ProducedType result = unit.getType(ed);
        ProducedType union =
                unit.getType(unit.getNothingDeclaration());
        int last = elemTypes.size()-1;
        for (int i=last; i>=0; i--) {
            ProducedType elemType = elemTypes.get(i);
            List<ProducedType> pair = 
                    new ArrayList<ProducedType>();
            pair.add(elemType);
            pair.add(union);
            UnionType ut = new UnionType(unit);
            ut.setCaseTypes(pair);
            union = ut.getType();
            if (variadic && i==last) {
                result = atLeastOne ? 
                        unit.getSequenceType(elemType) : 
                        unit.getSequentialType(elemType);
            }
            else {
                result = producedType(td, 
                        union, elemType, result);
                if (firstDefaulted>=0 && i>=firstDefaulted) {
                    pair = new ArrayList<ProducedType>();
                    pair.add(unit.getType(ed));
                    pair.add(result);
                    ut = new UnionType(unit);
                    ut.setCaseTypes(pair);
                    result = ut.getType();
                }
            }
        }
        return result;
    }
    
    @Override 
    public void visit(Tree.BaseType that) {
        super.visit(that);
        Tree.Identifier id = that.getIdentifier();
        if (id!=null) {
            String name = name(id);
            Scope scope = that.getScope();
            TypeDeclaration type; 
            if (that.getPackageQualified()) {
                type = getPackageTypeDeclaration(name, 
                        null, false, unit);
            }
            else {
                type = getTypeDeclaration(scope, name, 
                        null, false, unit);
            }
            if (type==null) {
                that.addError("type declaration does not exist: '" + 
                        name + "'", 102);
                unit.getUnresolvedReferences().add(id);
            }
            else {
                ProducedType outerType = 
                        scope.getDeclaringType(type);
                visitSimpleType(that, outerType, type);
            }
        }
    }
    
    public void visit(Tree.SuperType that) {
        //if (inExtendsClause) { //can't appear anywhere else in the tree!
            Scope scope = that.getScope();
            ClassOrInterface ci = 
                    getContainingClassOrInterface(scope);
            if (ci!=null) {
                if (scope instanceof Constructor) {
                    that.setTypeModel(intersectionOfSupertypes(ci));
                }
                else if (ci.isClassOrInterfaceMember()) {
                    ClassOrInterface oci = (ClassOrInterface) 
                            ci.getContainer();
                    that.setTypeModel(intersectionOfSupertypes(oci));
                }
                else {
                    that.addError("super appears in extends for non-member class");
                }
            }
        //}
    }
    
    @Override
    public void visit(Tree.MemberLiteral that) {
        super.visit(that);
        if (that.getType()!=null) {
            ProducedType pt = 
                    that.getType().getTypeModel();
            if (pt!=null) {
                if (that.getTypeArgumentList()!=null &&
                        isTypeUnknown(pt) && 
                        !pt.isUnknown()) {
                    that.getTypeArgumentList()
                        .addError("qualifying type does not fully-specify type arguments");
                }
            }
        }
    }
    
    @Override
    public void visit(Tree.QualifiedType that) {
        boolean onl = inTypeLiteral;
        boolean oiea = inExtendsOrClassAlias;
        boolean oidc = inDelegatedConstructor;
        inTypeLiteral = false;
        inExtendsOrClassAlias = false;
        inDelegatedConstructor = false;
        super.visit(that);
        inExtendsOrClassAlias = oiea;
        inDelegatedConstructor = oidc;
        inTypeLiteral = onl;
        
        Tree.StaticType ot = that.getOuterType();        
        ProducedType pt = ot.getTypeModel();
        if (pt!=null) {
//            if (pt.isTypeConstructor()) {
//                ot.addError("qualifying type may not be a type constructor");
//            }
            if (that.getMetamodel() && 
                    that.getTypeArgumentList()!=null &&
                    isTypeUnknown(pt) && !pt.isUnknown()) {
                that.getTypeArgumentList()
                    .addError("qualifying type does not fully-specify type arguments");
            }
            TypeDeclaration d = pt.getDeclaration();
            Tree.Identifier id = that.getIdentifier();
            if (id!=null) {
                String name = name(id);
                TypeDeclaration type = 
                        getTypeMember(d, name, 
                                null, false, unit);
                if (type==null) {
                    if (d.isMemberAmbiguous(name, unit, null, false)) {
                        that.addError("member type declaration is ambiguous: '" + 
                                name + "' for type '" + 
                                d.getName() + "'");
                    }
                    else {
                        that.addError("member type declaration does not exist: '" + 
                                name + "' in type '" + 
                                d.getName() + "'", 100);
                        unit.getUnresolvedReferences().add(id);
                    }
                }
                else {
                    visitSimpleType(that, pt, type);
                }
            }
        }
    }
    
    @Override
    public void visit(Tree.TypeLiteral that) {
        inTypeLiteral = true;
        super.visit(that);
        inTypeLiteral = false;
    }

    private void visitSimpleType(Tree.SimpleType that, 
            ProducedType ot, TypeDeclaration dec) {
        if (dec instanceof Constructor &&
                //in a metamodel type literal, a constructor
                //is allowed
                !inTypeLiteral && 
                //for an extends clause or aliased class, 
                //either a class with parameters or a 
                //constructor is allowed
                !inExtendsOrClassAlias && 
                !inDelegatedConstructor) {
            that.addError("constructor is not a type: '" + 
                    dec.getName(unit) + "'");
        }
        
        Tree.TypeArgumentList tal = 
                that.getTypeArgumentList();
        List<TypeParameter> params = 
                dec.getTypeParameters();
        List<ProducedType> typeArgs = 
                getTypeArguments(tal, ot, params);
        ProducedType pt = dec.getProducedType(ot, typeArgs);
        if (tal==null) {
            if (!params.isEmpty()) {
                //For now the only type constructors allowed
                //as the type of a value are type constructors
                //that alias Callable (in future relax this)
                //and interpret *every* type with a missing
                //type argument list as a type constructor
                if (dec.inherits(unit.getCallableDeclaration())) {
                    pt.setTypeConstructor(true);
                }
            }
        }
        else {
            if (params.isEmpty()) {
                that.addError("does not accept type arguments: '" + 
                        dec.getName(unit) + 
                        "' is not a generic type");
            }
            tal.setTypeModels(typeArgs);
            //TODO: dupe of logic in ExpressionVisitor
            List<Tree.Type> args = tal.getTypes();
            for (int i = 0; 
                    i<args.size() && 
                    i<params.size(); 
                    i++) {
                Tree.Type t = args.get(i);
                if (t instanceof Tree.StaticType) {
                    Tree.StaticType st = 
                            (Tree.StaticType) t;
                    Tree.TypeVariance variance = 
                            st.getTypeVariance();
                    if (variance!=null) {
                        TypeParameter p = params.get(i);
                        if (p.isInvariant()) {
                            String var = variance.getText();
                            if (var.equals("out")) {
                                pt.setVariance(p, OUT);
                            }
                            else if (var.equals("in")) {
                                pt.setVariance(p, IN);
                            }
                        }
                    }
                }
            }
        }
        that.setTypeModel(pt);
        that.setDeclarationModel(dec);
    }

    @Override 
    public void visit(Tree.VoidModifier that) {
        Class vtd = unit.getAnythingDeclaration();
        if (vtd!=null) {
            that.setTypeModel(vtd.getType());
        }
    }

    @Override 
    public void visit(Tree.SequencedType that) {
        super.visit(that);
        ProducedType type = 
                that.getType().getTypeModel();
        if (type!=null) {
            ProducedType et = that.getAtLeastOne() ? 
                    unit.getSequenceType(type) : 
                    unit.getSequentialType(type);
            that.setTypeModel(et);
        }
    }

    @Override 
    public void visit(Tree.DefaultedType that) {
        super.visit(that);
        ProducedType type = 
                that.getType().getTypeModel();
        if (type!=null) {
            that.setTypeModel(type);
        }
    }

    @Override 
    public void visit(Tree.SpreadType that) {
        super.visit(that);
        Tree.Type t = that.getType();
        if (t!=null) {
            ProducedType type = t.getTypeModel();
            if (type!=null) {
                that.setTypeModel(type);
            }
        }
    }

    @Override 
    public void visit(Tree.TypedDeclaration that) {
        super.visit(that);
        Type type = that.getType();
        TypedDeclaration dec = that.getDeclarationModel();
        setType(that, type, dec);
        if (dec instanceof MethodOrValue) {
            MethodOrValue mv = (MethodOrValue) dec;
            if (dec.isLate() && 
                    mv.isParameter()) {
                that.addError("parameter may not be annotated late");
            }
        }
//        if (type.getTypeModel().isTypeConstructor()) {
//            type.addError("type constructor may not occur as the type of a declaration");
//        }
    }

    @Override 
    public void visit(Tree.TypedArgument that) {
        super.visit(that);
        setType(that, that.getType(), 
                that.getDeclarationModel());
    }
        
    /*@Override 
    public void visit(Tree.FunctionArgument that) {
        super.visit(that);
        setType(that, that.getType(), that.getDeclarationModel());
    }*/
        
    private void setType(Node that, Tree.Type type, 
            TypedDeclaration td) {
        if (type==null) {
            that.addError("missing type of declaration: '" + 
                    td.getName() + "'");
        }
        else if (!(type instanceof Tree.LocalModifier)) { //if the type declaration is missing, we do type inference later
            ProducedType t = type.getTypeModel();
            if (t!=null) {
                td.setType(t);
            }
        }
    }
    
    private void defaultSuperclass(Tree.ExtendedType et, 
            TypeDeclaration cd) {
        if (et==null) {
            Class bd = unit.getBasicDeclaration();
            if (bd!=null) {
                cd.setExtendedType(bd.getType());
            }
        }
    }

    @Override 
    public void visit(Tree.ObjectDefinition that) {
        Class o = that.getAnonymousClass();
        o.setExtendedType(null);
        o.getSatisfiedTypes().clear();
        defaultSuperclass(that.getExtendedType(), o);
        super.visit(that);
        ProducedType type = o.getType();
        that.getDeclarationModel().setType(type);
        that.getType().setTypeModel(type);
    }

    @Override 
    public void visit(Tree.ObjectArgument that) {
        Class o = that.getAnonymousClass();
        o.setExtendedType(null);
        o.getSatisfiedTypes().clear();
        defaultSuperclass(that.getExtendedType(), o);
        super.visit(that);
        ProducedType type = o.getType();
        that.getDeclarationModel().setType(type);
        that.getType().setTypeModel(type);
    }

    @Override 
    public void visit(Tree.ObjectExpression that) {
        Class o = that.getAnonymousClass();
        o.setExtendedType(null);
        o.getSatisfiedTypes().clear();
        defaultSuperclass(that.getExtendedType(), o);
        super.visit(that);
    }

    @Override 
    public void visit(Tree.ClassDefinition that) {
        Class cd = that.getDeclarationModel();
        cd.setExtendedType(null);
        cd.getSatisfiedTypes().clear();
        Class vd = unit.getAnythingDeclaration();
        if (vd != null && !vd.equals(cd)) {
            defaultSuperclass(that.getExtendedType(), cd);
        }
        super.visit(that);
        Tree.ParameterList pl = that.getParameterList();
        if (pl!=null && cd.hasConstructors()) {
            pl.addError("class with parameters may not declare constructors: class '" + 
                    cd.getName() + 
                    "' has a parameter list and a constructor");
        }
        if (pl==null && !cd.hasConstructors()) {
            that.addError("class without parameters must declare at least one constructor: class '" + 
                    cd.getName() + 
                    "' has neither parameter list nor constructors", 
                    1001);
        }
    }

    @Override 
    public void visit(Tree.InterfaceDefinition that) {
        Interface id = that.getDeclarationModel();
        id.setExtendedType(null);
        id.getSatisfiedTypes().clear();
        Class od = unit.getObjectDeclaration();
        if (od!=null) {
            id.setExtendedType(od.getType());
        }
        super.visit(that);
    }

    @Override
    public void visit(Tree.TypeParameterDeclaration that) {
        TypeParameter p = that.getDeclarationModel();
        p.setExtendedType(null);
        p.getSatisfiedTypes().clear();
        Class vd = unit.getAnythingDeclaration();
        if (vd!=null) {
            p.setExtendedType(vd.getType());
        }
        
        super.visit(that);
        
        Tree.TypeSpecifier ts = that.getTypeSpecifier();
        if (ts!=null) {
            Tree.StaticType type = ts.getType();
            if (type!=null) {
                ProducedType dta = type.getTypeModel();
                Declaration dec = p.getDeclaration();
                if (dta!=null && 
                        dta.involvesDeclaration(dec)) {
                    type.addError("default type argument involves parameterized type: '" + 
                            dta.getProducedTypeName(unit) + 
                            "' involves '" + dec.getName(unit) + 
                            "'");
                    dta = null;
                }
                /*else if (dta.containsTypeParameters()) {
                    type.addError("default type argument involves type parameters: " + 
                            dta.getProducedTypeName(unit));
                    dta = null;
                }*/
                p.setDefaultTypeArgument(dta);
            }
        }
        
    }
    
    @Override
    public void visit(Tree.TypeParameterList that) {
        super.visit(that);
        List<Tree.TypeParameterDeclaration> tpds = 
                that.getTypeParameterDeclarations();
        List<TypeParameter> params = 
                new ArrayList<TypeParameter>
                    (tpds.size());
        for (int i=tpds.size()-1; i>=0; i--) {
            Tree.TypeParameterDeclaration tpd = tpds.get(i);
            if (tpd!=null) {
                TypeParameter tp = 
                        tpd.getDeclarationModel();
                ProducedType dta = 
                        tp.getDefaultTypeArgument();
                if (dta!=null) {
                    params.add(tp);
                    if (dta.involvesTypeParameters(params)) {
                        tpd.getTypeSpecifier()
                            .addError("default type argument involves a type parameter not yet declared");
                    }
                }
            }
        }
    }
    
    @Override 
    public void visit(Tree.ClassDeclaration that) {
        ClassAlias td = 
                (ClassAlias) that.getDeclarationModel();
        td.setExtendedType(null);
        super.visit(that);
        Tree.ClassSpecifier cs = 
                that.getClassSpecifier();
        if (cs==null) {
            that.addError("missing class body or aliased class reference");
        }
        else {
            Tree.ExtendedType et = 
                    that.getExtendedType();
            if (et!=null) {
                et.addError("class alias may not extend a type");
            }
            Tree.SatisfiedTypes sts = 
                    that.getSatisfiedTypes();
            if (sts!=null) {
                sts.addError("class alias may not satisfy a type");
            }
            Tree.CaseTypes cts = 
                    that.getCaseTypes();
            if (cts!=null) {
                that.addError("class alias may not have cases or a self type");
            }
            Tree.SimpleType ct = cs.getType();
            if (ct==null) {
//                that.addError("malformed aliased class");
            }
            else if (!(ct instanceof Tree.StaticType)) {
                cs.addError("aliased type must be a class");
            }
            /*else if (ct instanceof Tree.QualifiedType) {
                cs.addError("aliased class may not be a qualified type");
            }*/
            else {
                ProducedType type = ct.getTypeModel();
                if (type!=null) {
                    /*if (type.containsTypeAliases()) {
                        et.addError("aliased type involves type aliases: " +
                                type.getProducedTypeName());
                    }
                    else*/
                    TypeDeclaration dec = 
                            type.getDeclaration();
                    td.setConstructor(dec);
                    if (dec instanceof Constructor) {
                        type = type.getExtendedType();
                        dec = dec.getExtendedTypeDeclaration();
                    }
                    if (dec instanceof Class) {
                        td.setExtendedType(type);
                    }
                    else {
                        ct.addError("not a class: '" + 
                                dec.getName(unit) + "'");
                    }
                    TypeDeclaration etd = 
                            ct.getDeclarationModel();
                    if (etd==td) {
                        //TODO: handle indirect circularities!
                        ct.addError("directly aliases itself: '" + 
                                td.getName() + "'");
                    }
                }
            }
        }
    }
    
    @Override 
    public void visit(Tree.InterfaceDeclaration that) {
        Interface id = that.getDeclarationModel();
        id.setExtendedType(null);
        super.visit(that);
        if (that.getTypeSpecifier()==null) {
            that.addError("missing interface body or aliased interface reference");
        }
        else {
            Tree.SatisfiedTypes sts = 
                    that.getSatisfiedTypes();
            if (sts!=null) {
                sts.addError("interface alias may not satisfy a type");
            }
            Tree.CaseTypes cts = 
                    that.getCaseTypes();
            if (cts!=null) {
                that.addError("class alias may not have cases or a self type");
            }
            Tree.StaticType et = 
                    that.getTypeSpecifier()
                        .getType();
            if (et==null) {
//                that.addError("malformed aliased interface");
            }
            else if (!(et instanceof Tree.StaticType)) {
                that.getTypeSpecifier()
                        .addError("aliased type must be an interface");
            }
            else {
                ProducedType type = et.getTypeModel();
                if (type!=null) {
                    /*if (type.containsTypeAliases()) {
                        et.addError("aliased type involves type aliases: " +
                                type.getProducedTypeName());
                    }
                    else*/ 
                    if (type.getDeclaration() instanceof Interface) {
                        id.setExtendedType(type);
                    } 
                    else {
                        et.addError("not an interface: '" + 
                                type.getDeclaration().getName(unit) + 
                                "'");
                    }
                }
            }
        }
    }
    
    @Override 
    public void visit(Tree.TypeAliasDeclaration that) {
        TypeAlias ta = that.getDeclarationModel();
        ta.setExtendedType(null);
        super.visit(that);
        Tree.SatisfiedTypes sts = 
                that.getSatisfiedTypes();
        if (sts!=null) {
            sts.addError("type alias may not satisfy a type");
        }
        if (that.getTypeSpecifier()==null) {
            that.addError("missing aliased type");
        }
        else {
            Tree.StaticType et = 
                    that.getTypeSpecifier()
                        .getType();
            if (et==null) {
                that.addError("malformed aliased type");
            }
            else {
                ProducedType type = et.getTypeModel();
                if (type!=null) {
                    /*if (type.containsTypeAliases()) {
                        et.addError("aliased type involves type aliases: " +
                                type.getProducedTypeName());
                    }
                    else {*/
                        ta.setExtendedType(type);
                    //}
                }
            }
        }
    }
    
    private boolean isInitializerParameter(MethodOrValue dec) {
        return dec!=null && 
                dec.isParameter() && 
                dec.getInitializerParameter()
                    .isHidden();
    }
    
    @Override
    public void visit(Tree.MethodDeclaration that) {
        super.visit(that);
        Tree.SpecifierExpression sie = 
                that.getSpecifierExpression();
        Method dec = that.getDeclarationModel();
        if (isInitializerParameter(dec)) {
            if (sie!=null) {
                sie.addError("function is an initializer parameter and may not have an initial value: '" + 
                        dec.getName() + "'");
            }
        }
        if (sie==null && isNativeImplementation(dec)) {
            that.addError("missing method body for native function implementation");
        }
    }
    
    @Override
    public void visit(Tree.MethodDefinition that) {
        super.visit(that);
        Method dec = that.getDeclarationModel();
        if (isInitializerParameter(dec)) {
            that.getBlock()
                .addError("function is an initializer parameter and may not have a body: '" + 
                        dec.getName() + "'");
        }
    }
    
    @Override
    public void visit(Tree.AttributeDeclaration that) {
        super.visit(that);
        Tree.SpecifierOrInitializerExpression sie = 
                that.getSpecifierOrInitializerExpression();
        Value dec = that.getDeclarationModel();
        if (isInitializerParameter(dec)) {
            Parameter param = dec.getInitializerParameter();
            Tree.Type type = that.getType();
            if (type instanceof Tree.SequencedType) {
                param.setSequenced(true);
                Tree.SequencedType st = 
                        (Tree.SequencedType) type;
                param.setAtLeastOne(st.getAtLeastOne());
            }
            if (sie!=null) {
                sie.addError("value is an initializer parameter and may not have an initial value: '" + 
                        dec.getName() + "'");
            }
        }
        if (sie==null && isNativeImplementation(dec)) {
            that.addError("missing method body for native value implementation");
        }
    }
    
    @Override
    public void visit(Tree.AttributeGetterDefinition that) {
        super.visit(that);
        Value dec = that.getDeclarationModel();
        if (isInitializerParameter(dec)) {
            that.getBlock()
                .addError("value is an initializer parameter and may not have a body: '" + 
                        dec.getName() + "'");
        }
    }
    
    void checkExtendedTypeExpression(Tree.Type type) {
        if (type instanceof Tree.QualifiedType) {
            Tree.QualifiedType qualifiedType = 
                    (Tree.QualifiedType) type;
            Tree.StaticType outerType = 
                    qualifiedType.getOuterType();
            if (!(outerType instanceof Tree.SuperType)) {
                TypeDeclaration otd = 
                        qualifiedType.getDeclarationModel();
                if (otd!=null) {
                    if (otd.isStaticallyImportable() || 
                            otd instanceof Constructor) {
                        checkExtendedTypeExpression(outerType);
                    }
                    else {
                        outerType.addError("illegal qualifier in constructor delegation (must be super)");
                    }
                }
            }
        }
    }
    
    @Override 
    public void visit(Tree.DelegatedConstructor that) {
        inDelegatedConstructor = true;
        super.visit(that);
        inDelegatedConstructor = false;
        checkExtendedTypeExpression(that.getType());
    }

    @Override 
    public void visit(Tree.ClassSpecifier that) {
        inExtendsOrClassAlias = true;
        super.visit(that);
        inExtendsOrClassAlias = false;
        checkExtendedTypeExpression(that.getType());
    }
    
    @Override 
    public void visit(Tree.ExtendedType that) {
        inExtendsOrClassAlias = 
                that.getInvocationExpression()!=null;
        super.visit(that);
        inExtendsOrClassAlias = false;
        checkExtendedTypeExpression(that.getType());
        TypeDeclaration td = 
                (TypeDeclaration) that.getScope();
        if (!td.isAlias()) {
            Tree.SimpleType et = that.getType();
            if (et!=null) {
                ProducedType type = et.getTypeModel();
                if (type!=null) {
                    TypeDeclaration etd = et.getDeclarationModel();
                    if (etd!=null && 
                            !(etd instanceof UnknownType)) {
                        if (etd instanceof Constructor) {
                            type = type.getExtendedType();
                            etd = etd.getExtendedTypeDeclaration();
                        }
                        if (etd==td) {
                            //unnecessary, handled by SupertypeVisitor
//                          et.addError("directly extends itself: '" + 
//                                  td.getName() + "'");
                        }
                        else if (etd instanceof TypeParameter) {
                            et.addError("directly extends a type parameter: '" + 
                                    type.getDeclaration().getName(unit) + 
                                    "'");
                        }
                        else if (etd instanceof Interface) {
                            et.addError("extends an interface: '" + 
                                    type.getDeclaration().getName(unit) + 
                                    "'");
                        }
                        else if (etd instanceof TypeAlias) {
                            et.addError("extends a type alias: '" + 
                                    type.getDeclaration().getName(unit) + 
                                    "'");
                        }
                        else {
                            td.setExtendedType(type);
                        }
                    }
                }
            }
        }
    }
    
    @Override 
    public void visit(Tree.SatisfiedTypes that) {
        super.visit(that);
        TypeDeclaration td = 
                (TypeDeclaration) that.getScope();
        if (td.isAlias()) {
            return;
        }
        List<ProducedType> list = 
                new ArrayList<ProducedType>
                    (that.getTypes().size());
        if (that.getTypes().isEmpty()) {
            that.addError("missing types in satisfies");
        }
        boolean foundTypeParam = false;
        boolean foundClass = false;
        boolean foundInterface = false;
        for (Tree.StaticType st: that.getTypes()) {
            ProducedType type = st.getTypeModel();
            if (type!=null) {
                TypeDeclaration std = type.getDeclaration();
                if (std!=null && 
                        !(std instanceof UnknownType)) {
                    if (std==td) {
                        //unnecessary, handled by SupertypeVisitor
//                      st.addError("directly extends itself: '" + 
//                              td.getName() + "'");
                        continue;
                    }
                    if (std instanceof TypeAlias) {
                        st.addError("satisfies a type alias: '" + 
                                type.getDeclaration().getName(unit) + 
                                "'");
                        continue;
                    }
                    if (std instanceof Constructor) {
                        continue;
                    }
                    if (td instanceof TypeParameter) {
                        if (foundTypeParam) {
                            st.addUnsupportedError("type parameter upper bounds are not yet supported in combination with other bounds");
                        }
                        else if (std instanceof TypeParameter) {
                            if (foundClass||foundInterface) {
                                st.addUnsupportedError("type parameter upper bounds are not yet supported in combination with other bounds");
                            }
                            foundTypeParam = true;
                        }
                        else if (std instanceof Class) {
                            if (foundClass) {
                                st.addUnsupportedError("multiple class upper bounds are not yet supported");
                            }
                            foundClass = true;
                        }
                        else if (std instanceof Interface) {
                            foundInterface = true;
                        }
                        else {
                            st.addError("upper bound must be a class, interface, or type parameter");
                            continue;
                        }
                    } 
                    else {
                        if (std instanceof TypeParameter) {
                            st.addError("directly satisfies type parameter: '" + 
                                    std.getName(unit) + "'");
                            continue;
                        }
                        else if (std instanceof Class) {
                            st.addError("satisfies a class: '" + 
                                    std.getName(unit) + "'");
                            continue;
                        }
                        else if (std instanceof Interface) {
                            if (td.isDynamic() && 
                                    !std.isDynamic()) {
                                st.addError("dynamic interface satisfies a non-dynamic interface: '" + 
                                        std.getName(unit) + "'");
                            }
                        }
                        else {
                            st.addError("satisfied type must be an interface");
                            continue;
                        }
                    }
                    list.add(type);
                }
            }
        }
        td.setSatisfiedTypes(list);
    }
    
    /*@Override 
    public void visit(Tree.TypeConstraint that) {
        super.visit(that);
        if (that.getSelfType()!=null) {
            TypeDeclaration td = (TypeDeclaration) that.getSelfType().getScope();
            TypeParameter tp = that.getDeclarationModel();
            td.setSelfType(tp.getType());
            if (tp.isSelfType()) {
                that.addError("type parameter may not act as self type for two different types");
            }
            else {
                tp.setSelfTypedDeclaration(td);
            }
        }
    }*/

    @Override 
    public void visit(Tree.CaseTypes that) {
        super.visit(that);
        TypeDeclaration td = 
                (TypeDeclaration) that.getScope();
        List<Tree.BaseMemberExpression> bmes = 
                that.getBaseMemberExpressions();
        List<Tree.StaticType> cts = that.getTypes();
        List<ProducedType> list = 
                new ArrayList<ProducedType>
                    (bmes.size()+cts.size());
        if (td instanceof TypeParameter) {
            if (!bmes.isEmpty()) {
                that.addError("cases of type parameter must be a types");
            }
        }
        else {
            for (Tree.BaseMemberExpression bme: bmes) {
                //bmes have not yet been resolved
                TypedDeclaration od = 
                        getTypedDeclaration(bme.getScope(), 
                                name(bme.getIdentifier()), 
                                null, false, bme.getUnit());
                if (od!=null) {
                    ProducedType type = od.getType();
                    if (type!=null) {
                        list.add(type);
                    }
                }
            }
        }
        for (Tree.StaticType ct: cts) {
            ProducedType type = ct.getTypeModel();
            if (type!=null) {
                TypeDeclaration ctd = type.getDeclaration();
                if (ctd!=null && 
                        !(ctd instanceof UnknownType)) {
                    if (ctd instanceof UnionType || 
                        ctd instanceof IntersectionType) {
                        //union/intersection types don't have equals()
                        if (td instanceof TypeParameter) {
                            ct.addError("enumerated bound must be a class or interface type");
                        }
                        else {
                            ct.addError("case type must be a class, interface, or self type");
                        }
                    }
                    else if (ctd.equals(td)) {
                        ct.addError("directly enumerates itself: '" + 
                                td.getName() + "'");
                        continue;
                    }
                    else if (ctd instanceof TypeParameter) {
                        if (!(td instanceof TypeParameter)) {
                            TypeParameter tp = 
                                    (TypeParameter) ctd;
                            td.setSelfType(type);
                            if (tp.isSelfType()) {
                                ct.addError("type parameter may not act as self type for two different types");
                            }
                            else {
                                tp.setSelfTypedDeclaration(td);
                            }
                            if (cts.size()>1) {
                                ct.addError("a type may not have more than one self type");
                            }
                        }
                        else {
                            //TODO: error?!
                        }
                    }
                    else if (ctd instanceof ClassOrInterface) {
                        //nothing special to do
                    }
                    else {
                        if (td instanceof TypeParameter) {
                            ct.addError("enumerated bound must be a class or interface type");
                        }
                        else {
                            ct.addError("case type must be a class, interface, or self type");
                        }
                        continue;
                    }
                    list.add(type);
                }
            }
        }
        if (!list.isEmpty()) {
            if (list.size() == 1 && 
                    list.get(0).getDeclaration()
                        .isSelfType()) {
                Scope scope = 
                        list.get(0)
                            .getDeclaration()
                            .getContainer();
                if (scope instanceof ClassOrInterface && 
                        !((ClassOrInterface) scope).isAbstract()) {
                    that.addError("non-abstract class parameterized by self type: '" + 
                            td.getName() + "'", 905);
                }
            }
            else {
                if (td instanceof ClassOrInterface && 
                        !((ClassOrInterface) td).isAbstract()) {
                    that.addError("non-abstract class has enumerated subtypes: '" +
                            td.getName() + "'", 905);
                }
            }
            td.setCaseTypes(list);
        }
    }

    @Override
    public void visit(Tree.InitializerParameter that) {
        super.visit(that);
        //i.e. an attribute initializer parameter
        Parameter p = that.getParameterModel();
        Declaration a = 
                that.getScope()
                    .getDirectMember(p.getName(), 
                            null, false);
        if (a==null) {
            //Now done in ExpressionVisitor!
//            that.addError("parameter declaration does not exist: '" + p.getName() + "'");
        }
        else if (!isLegalParameter(a)) {
            that.addError("parameter is not a reference value or function: '" + 
                    p.getName() + "'");
        }
        else {
            if (a.isFormal()) {
                that.addError("parameter is a formal attribute: '" + 
                        p.getName() + "'", 320);
            }
            /*else if (a.isDefault()) {
                that.addError("initializer parameter refers to a default attribute: " + 
                        d.getName());
            }*/
            MethodOrValue mov = (MethodOrValue) a;
            mov.setInitializerParameter(p);
            p.setModel(mov);
        }
        if (a instanceof Generic && 
                !((Generic) a).getTypeParameters().isEmpty()) {
            that.addError("parameter declaration has type parameters: '" + 
                    p.getName() + "' may not declare type parameters");
        }
        if (p.isDefaulted()) {
            checkDefaultArg(that.getSpecifierExpression(), p);
        }
    }

    public boolean isLegalParameter(Declaration a) {
        if (a instanceof Value) {
            Value v = (Value) a;
            if (v.isTransient()) {
                return false;
            }
            else {
                TypeDeclaration td = v.getTypeDeclaration();
                return !(td instanceof Class) || 
                        !td.isAnonymous();
            }
        }
        else if (a instanceof Method) {
            return true;
        }
        else {
            return false;
        }
    }
    
    @Override
    public void visit(Tree.AnyAttribute that) {
        super.visit(that);
        Tree.Type type = that.getType();
        if (type instanceof Tree.SequencedType) {
            Value v = (Value) that.getDeclarationModel();
            Parameter p = v.getInitializerParameter();
            if (p==null) {
                type.addError("value is not a parameter, so may not be variadic: '" +
                        v.getName() + "'");
            }
            else {
                p.setSequenced(true);
            }
        }
    }

    @Override
    public void visit(Tree.AnyMethod that) {
        super.visit(that);
        Tree.Type type = that.getType();
        if (type instanceof Tree.SequencedType) {
            type.addError("function type may not be variadic");
        }
    }
    
    @Override 
    public void visit(Tree.QualifiedMemberOrTypeExpression that) {
        Tree.Primary primary = that.getPrimary();
        if (primary instanceof Tree.MemberOrTypeExpression) {
            Tree.MemberOrTypeExpression mte = 
                    (Tree.MemberOrTypeExpression) primary;
            if (mte instanceof Tree.BaseTypeExpression || 
                mte instanceof Tree.QualifiedTypeExpression) {
                that.setStaticMethodReference(true);
                mte.setStaticMethodReferencePrimary(true);
                if (that.getDirectlyInvoked()) {
                    mte.setDirectlyInvoked(true);
                }
            }
        }
        if (primary instanceof Tree.Package) {
            ((Tree.Package) primary).setQualifier(true);
        }
        super.visit(that);
    }

    @Override 
    public void visit(Tree.InvocationExpression that) {
        Tree.Term primary = 
                unwrapExpressionUntilTerm(that.getPrimary());
        if (primary instanceof Tree.MemberOrTypeExpression) {
            Tree.MemberOrTypeExpression mte = 
                    (Tree.MemberOrTypeExpression) primary;
            mte.setDirectlyInvoked(true);
        }
        super.visit(that);
    }

    private static Tree.SpecifierOrInitializerExpression 
    getSpecifier(Tree.ParameterDeclaration that) {
        Tree.TypedDeclaration dec = 
                that.getTypedDeclaration();
        if (dec instanceof Tree.AttributeDeclaration) {
            Tree.AttributeDeclaration ad = 
                    (Tree.AttributeDeclaration) dec;
            return ad.getSpecifierOrInitializerExpression();
        }
        else if (dec instanceof Tree.MethodDeclaration) {
            Tree.MethodDeclaration md = 
                    (Tree.MethodDeclaration) dec;
            return md.getSpecifierExpression();
        }
        else {
            return null;
        }
    }
    
    private void checkDefaultArg(Tree.SpecifierOrInitializerExpression se, 
            Parameter p) {
        if (se!=null) {
            if (se.getScope() instanceof Specification) {
                se.addError("parameter of specification statement may not define default value");
            }
            else {
                Declaration d = p.getDeclaration();
                if (d.isActual()) {
                    se.addError("parameter of actual declaration may not define default value: parameter '" +
                            p.getName() + "' of '" + 
                            p.getDeclaration().getName() + 
                            "'");
                }
            }
        }
    }
    
    @Override public void visit(Tree.ParameterDeclaration that) {
        super.visit(that);
        Parameter p = that.getParameterModel();
        if (p.isDefaulted()) {
            if (p.getDeclaration().isParameter()) {
                getSpecifier(that)
                    .addError("parameter of callable parameter may not have default argument");
            }
            checkDefaultArg(getSpecifier(that), p);
        }
    }
    
}
